-- Onda 5: administração de empresas contratantes (RF27) e troca de tenant na sessão.
--
-- refresh_tokens.tenant_id guarda o tenant ativo da sessão quando um administrador alterna de
-- empresa pela casca. Nulo significa o tenant de origem do próprio usuário (usuarios.tenant_id).

ALTER TABLE identity.refresh_tokens
    ADD COLUMN tenant_id uuid REFERENCES identity.tenants(id);
