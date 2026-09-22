-- Onda 2: convite e recuperação de senha, sessões ativas e administração de usuários.
--
-- Desvios do Modelo v0.2, a registrar na v0.3:
--   usuarios.telefone                       cadastro de usuário (Requisito RF13)
--   refresh_tokens.sessao_id, iniciada_em   a rotação grava uma linha por renovação; a sessão
--                                           precisa de um identificador que sobreviva a ela (RF06)

ALTER TABLE identity.usuarios ADD COLUMN telefone varchar(20);

ALTER TABLE identity.refresh_tokens
    ADD COLUMN sessao_id   uuid,
    ADD COLUMN iniciada_em timestamptz;
UPDATE identity.refresh_tokens SET sessao_id = id, iniciada_em = created_at;
ALTER TABLE identity.refresh_tokens
    ALTER COLUMN sessao_id SET NOT NULL,
    ALTER COLUMN iniciada_em SET NOT NULL;
CREATE INDEX idx_refresh_sessao ON identity.refresh_tokens(usuario_id, sessao_id) WHERE revogado_em IS NULL;

-- Links pendentes do usuário e o limite de pedidos de recuperação por e-mail
CREATE INDEX idx_recuperacoes_usuario ON identity.recuperacoes_senha(usuario_id, created_at DESC);

-- O cliente é a Centinela Soluções (confirmado em 21/09/2026). A V2 gravou valores provisórios;
-- razão social completa e CNPJ ainda não foram informados.
UPDATE identity.tenants
   SET razao_social = 'Centinela Soluções', nome_fantasia = 'Centinela Soluções', subdominio = 'centinela',
       updated_at = now()
 WHERE id = '00000000-0000-4000-8000-000000000001' AND razao_social = 'Empresa contratante';
