-- Onda 4: segundo fator (RF10), busca global (RF55) e anonimização do usuário (RF56).
-- O tema na conta (RF40) usa usuarios.preferencia_tema, que existe desde a V1.
--
-- Desvios do Modelo v0.2, a registrar na v0.3:
--   usuarios.mfa_secret            passa a guardar o segredo TOTP cifrado (AES-GCM, chave do ambiente)
--   usuarios.mfa_*                 colunas novas do segundo fator, abaixo
--   usuarios.anonimizado_em        o id fica, para a autoria dos registros continuar de pé
--   codigos_recuperacao            tabela técnica, como refresh_tokens: sem tenant_id, presa ao usuário
--   desafios_segundo_fator         idem; vive 5 minutos entre a senha e o código
--   modulos.busca                  o módulo responde GET /api/{modulo}/busca (Contrato §8.6)

ALTER TABLE identity.usuarios
    ADD COLUMN mfa_ativado_em      timestamptz,
    ADD COLUMN mfa_ultimo_passo    bigint,       -- passo de 30 s do último código aceito: impede reuso
    ADD COLUMN mfa_secret_pendente text,         -- troca de aparelho em andamento, cifrado como mfa_secret
    ADD COLUMN anonimizado_em      timestamptz;

-- Dez por usuário, de uso único; o banco guarda só o SHA-256 (o código vale como senha)
CREATE TABLE identity.codigos_recuperacao (
    id         uuid PRIMARY KEY,
    usuario_id uuid NOT NULL REFERENCES identity.usuarios(id) ON DELETE CASCADE,
    hash       text NOT NULL,
    usado_em   timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_codigos_recuperacao_usuario ON identity.codigos_recuperacao(usuario_id) WHERE usado_em IS NULL;

-- Senha certa, código ainda não: o desafio liga as duas etapas do login sem emitir sessão
CREATE TABLE identity.desafios_segundo_fator (
    id                 uuid PRIMARY KEY,
    usuario_id         uuid NOT NULL REFERENCES identity.usuarios(id) ON DELETE CASCADE,
    token_hash         text NOT NULL UNIQUE,
    cadastro           boolean NOT NULL,     -- true: o usuário ainda não tem segundo fator e vai cadastrar
    segredo_provisorio text,                 -- cifrado; só no cadastro, até o primeiro código confirmar
    falhas             integer NOT NULL DEFAULT 0,
    expira_em          timestamptz NOT NULL,
    usado_em           timestamptz,
    created_at         timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_desafios_expiracao ON identity.desafios_segundo_fator(expira_em);

ALTER TABLE identity.modulos ADD COLUMN busca boolean NOT NULL DEFAULT false;
