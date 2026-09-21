-- O único tenant de produção do semestre (Requisito RF30). A tela de cadastro de tenants fica
-- para quando houver mais de uma empresa. Os dez perfis dele são criados pelo serviço ao subir
-- (Modelo §7): a migration roda uma vez e não sabe quais tenants virão depois.
INSERT INTO identity.tenants (id, razao_social, nome_fantasia, subdominio)
VALUES ('00000000-0000-4000-8000-000000000001', 'Empresa contratante', 'Plataforma', 'principal')
ON CONFLICT (id) DO NOTHING;
