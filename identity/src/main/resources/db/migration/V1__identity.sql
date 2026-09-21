-- Schema identity — Modelo de Dados da Plataforma v0.2, §8. Roda como own_identity (Contrato §7.1).
--
-- Desvios do Modelo v0.2, a registrar na v0.3:
--   permissoes.perfis_padrao  perfis que recebem a permissão quando nascem (campo perfisPadrao do YAML)
--   permissoes.servicos       clientIds de token de serviço que recebem a permissão (campo servicos)
--   modulos.grupo             texto livre do registro do módulo (modulos/{codigo}.json)

-- Em produção a extensão já existe (criada pelo db/init do infra) e isto não faz nada.
-- Nos testes, com banco vazio, cria o tipo citext em public, onde toda conexão o enxerga.
CREATE EXTENSION IF NOT EXISTS citext SCHEMA public;

-- ------------------------------------------------------------------ identidade e acesso

CREATE TABLE identity.tenants (
    id            uuid PRIMARY KEY,
    razao_social  text NOT NULL,
    nome_fantasia text,
    cnpj          varchar(14) UNIQUE,              -- somente dígitos
    subdominio    varchar(63) UNIQUE,
    ativo         boolean NOT NULL DEFAULT true,   -- desativar bloqueia o login de todos os usuários
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    deleted_at    timestamptz
);

CREATE TABLE identity.usuarios (
    id               uuid PRIMARY KEY,
    tenant_id        uuid NOT NULL REFERENCES identity.tenants(id),
    nome             text NOT NULL,
    email            citext NOT NULL,
    senha_hash       text,                         -- BCrypt 12; nulo até o convite ser aceito
    mfa_ativo        boolean NOT NULL DEFAULT false,
    mfa_secret       text,
    preferencia_tema varchar(10) NOT NULL DEFAULT 'sistema'
                     CHECK (preferencia_tema IN ('claro', 'escuro', 'sistema')),
    ultimo_login_em  timestamptz,
    ativo            boolean NOT NULL DEFAULT true,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    deleted_at       timestamptz,
    created_by       uuid,
    updated_by       uuid
);
-- E-mail único global, só entre usuários não excluídos (Modelo §6.2, decisão D8)
CREATE UNIQUE INDEX uk_usuarios_email ON identity.usuarios(email) WHERE deleted_at IS NULL;
CREATE INDEX idx_usuarios_tenant ON identity.usuarios(tenant_id) WHERE deleted_at IS NULL;

CREATE TABLE identity.perfis (
    id         uuid PRIMARY KEY,
    tenant_id  uuid NOT NULL REFERENCES identity.tenants(id),
    nome       text NOT NULL,
    descricao  text,
    sistema    boolean NOT NULL DEFAULT false,     -- os dez perfis iniciais; impede exclusão
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz,
    created_by uuid,
    updated_by uuid
);
CREATE UNIQUE INDEX uk_perfis_tenant_nome ON identity.perfis(tenant_id, nome) WHERE deleted_at IS NULL;

-- Catálogo global, sem tenant_id (Modelo §6.1)
CREATE TABLE identity.permissoes (
    id            uuid PRIMARY KEY,
    codigo        text NOT NULL UNIQUE,
    modulo        text NOT NULL,
    recurso       text NOT NULL,
    acao          text NOT NULL,
    descricao     text NOT NULL,
    perfis_padrao text[] NOT NULL DEFAULT '{}',
    servicos      text[] NOT NULL DEFAULT '{}'
);
CREATE INDEX idx_permissoes_modulo ON identity.permissoes(modulo);

CREATE TABLE identity.perfil_permissoes (
    perfil_id    uuid NOT NULL REFERENCES identity.perfis(id) ON DELETE CASCADE,
    permissao_id uuid NOT NULL REFERENCES identity.permissoes(id),
    PRIMARY KEY (perfil_id, permissao_id)
);

CREATE TABLE identity.usuario_perfis (
    usuario_id uuid NOT NULL REFERENCES identity.usuarios(id) ON DELETE CASCADE,
    perfil_id  uuid NOT NULL REFERENCES identity.perfis(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (usuario_id, perfil_id)
);

CREATE TABLE identity.equipes (
    id         uuid PRIMARY KEY,
    tenant_id  uuid NOT NULL REFERENCES identity.tenants(id),
    nome       text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz,
    created_by uuid,
    updated_by uuid
);
CREATE UNIQUE INDEX uk_equipes_tenant_nome ON identity.equipes(tenant_id, nome) WHERE deleted_at IS NULL;

CREATE TABLE identity.usuario_equipes (
    usuario_id uuid NOT NULL REFERENCES identity.usuarios(id) ON DELETE CASCADE,
    equipe_id  uuid NOT NULL REFERENCES identity.equipes(id) ON DELETE CASCADE,
    lider      boolean NOT NULL DEFAULT false,     -- quem enxerga a equipe inteira
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (usuario_id, equipe_id)
);
CREATE INDEX idx_usuario_equipes_equipe ON identity.usuario_equipes(equipe_id);

-- Sessões ativas. Guarda o SHA-256 do token do cookie, nunca o token.
CREATE TABLE identity.refresh_tokens (
    id          uuid PRIMARY KEY,
    usuario_id  uuid NOT NULL REFERENCES identity.usuarios(id) ON DELETE CASCADE,
    token_hash  text NOT NULL UNIQUE,
    expira_em   timestamptz NOT NULL,              -- fim da sessão; a rotação não estende
    revogado_em timestamptz,
    ip          inet,
    user_agent  text,
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_usuario ON identity.refresh_tokens(usuario_id) WHERE revogado_em IS NULL;

-- Convite e recuperação de senha na mesma tabela (Modelo §6.7). Usada a partir da onda 2.
CREATE TABLE identity.recuperacoes_senha (
    id         uuid PRIMARY KEY,
    usuario_id uuid NOT NULL REFERENCES identity.usuarios(id) ON DELETE CASCADE,
    tipo       varchar(12) NOT NULL CHECK (tipo IN ('convite', 'recuperacao')),
    token_hash text NOT NULL UNIQUE,
    expira_em  timestamptz NOT NULL,               -- 72 h no convite, 30 min na recuperação
    usado_em   timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);

-- Rate limit do login: 5 falhas em 15 minutos, por e-mail ou por IP (Prompt Mestre §84)
CREATE TABLE identity.tentativas_login (
    id         uuid PRIMARY KEY,
    email      citext NOT NULL,
    ip         inet,
    sucesso    boolean NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_tentativas_email ON identity.tentativas_login(email, created_at DESC) WHERE NOT sucesso;
CREATE INDEX idx_tentativas_ip ON identity.tentativas_login(ip, created_at DESC) WHERE NOT sucesso;

-- ------------------------------------------------------------------ registro de módulos

-- Característica da instalação, sem tenant_id (Modelo §6.3)
CREATE TABLE identity.modulos (
    id             uuid PRIMARY KEY,
    codigo         varchar(30) NOT NULL UNIQUE,    -- imutável
    nome           text NOT NULL,
    grupo          text,
    icone          text,                           -- nome de ícone do lucide, em kebab-case
    url_frontend   text NOT NULL,                  -- caminho na mesma origem: /modulos/crm/
    prefixo_api    text NOT NULL,
    permissao_menu text NOT NULL,
    ordem_menu     integer NOT NULL DEFAULT 100,
    healthcheck    text NOT NULL,
    ativo          boolean NOT NULL DEFAULT true,  -- desligar esconde o módulo sem apagar o registro
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE identity.modulo_menu_itens (
    id        uuid PRIMARY KEY,
    modulo_id uuid NOT NULL REFERENCES identity.modulos(id) ON DELETE CASCADE,
    rota      text NOT NULL,
    nome      text NOT NULL,
    permissao text NOT NULL,
    ordem     integer NOT NULL
);
CREATE INDEX idx_menu_itens_modulo ON identity.modulo_menu_itens(modulo_id, ordem);

-- ------------------------------------------------------------------ token de serviço

-- Da instalação, sem tenant_id: o tenant de cada chamada vai em X-Tenant-Id (Modelo §6.6)
CREATE TABLE identity.credenciais_servico (
    id                 uuid PRIMARY KEY,
    client_id          varchar(30) NOT NULL UNIQUE,
    client_secret_hash text NOT NULL,
    modulo_codigo      varchar(30) NOT NULL,
    ativo              boolean NOT NULL DEFAULT true,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE identity.credencial_permissoes (
    credencial_id uuid NOT NULL REFERENCES identity.credenciais_servico(id) ON DELETE CASCADE,
    permissao_id  uuid NOT NULL REFERENCES identity.permissoes(id),
    PRIMARY KEY (credencial_id, permissao_id)
);

-- ------------------------------------------------------------------ serviços transversais

-- Só insere: usr_identity perde UPDATE e DELETE logo abaixo (Modelo §6.5)
CREATE TABLE identity.audit_logs (
    id             uuid PRIMARY KEY,
    tenant_id      uuid,                           -- nulo: e-mail inexistente, token de serviço
    usuario_id     uuid,
    ip             inet,
    acao           text NOT NULL,
    entidade       text NOT NULL,
    entidade_id    uuid,
    valor_anterior jsonb,
    valor_novo     jsonb,
    created_at     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_tenant_data ON identity.audit_logs(tenant_id, created_at DESC);
CREATE INDEX idx_audit_entidade ON identity.audit_logs(entidade, entidade_id);

-- Timeline única da empresa, alimentada por identity.timeline.registrar (Modelo §6.4)
CREATE TABLE identity.eventos_timeline (
    id            uuid PRIMARY KEY,
    tenant_id     uuid NOT NULL,
    empresa_id    uuid NOT NULL,                   -- crm.empresas, sem FK entre schemas
    modulo_origem varchar(30) NOT NULL,
    tipo          text NOT NULL,
    texto         text NOT NULL,
    rota          text,
    usuario_id    uuid,
    ocorrido_em   timestamptz NOT NULL,            -- quando o fato aconteceu, não quando foi gravado
    created_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_timeline_empresa ON identity.eventos_timeline(tenant_id, empresa_id, ocorrido_em DESC);

-- Pedidas por identity.notificacao.criar; lida_em nulo significa não lida
CREATE TABLE identity.notificacoes (
    id         uuid PRIMARY KEY,
    tenant_id  uuid NOT NULL,
    usuario_id uuid NOT NULL REFERENCES identity.usuarios(id) ON DELETE CASCADE,
    categoria  text NOT NULL,
    titulo     text NOT NULL,
    texto      text,
    rota       text,
    lida_em    timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_notificacoes_usuario ON identity.notificacoes(tenant_id, usuario_id, created_at DESC);

-- Idempotência do consumo (Contrato §9.7): a mesma mensagem entregue duas vezes tem um efeito só
CREATE TABLE identity.eventos_processados (
    evento_id     uuid PRIMARY KEY,                -- o "id" do envelope da mensagem
    tipo          text NOT NULL,
    processado_em timestamptz NOT NULL DEFAULT now()
);

-- O IF EXISTS deixa a migration rodar nos testes, onde usr_identity não existe.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'usr_identity') THEN
    REVOKE UPDATE, DELETE, TRUNCATE ON identity.audit_logs FROM usr_identity;
  END IF;
END $$;
