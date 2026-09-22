-- Onda 3: notificações na casca, consulta da auditoria e limpeza das tabelas técnicas.
--
-- Desvio do Modelo v0.2, a registrar na v0.3:
--   notificacoes.modulo_origem   a rota da notificação é relativa ao front do módulo que a pediu
--                                (Contrato §12.2); sem o módulo, a casca não sabe onde abrir.
--                                Nulo nas notificações gravadas antes desta migration.

ALTER TABLE identity.notificacoes ADD COLUMN modulo_origem varchar(30);

-- Contador do sino, consultado a cada 30 segundos por usuário logado
CREATE INDEX idx_notificacoes_nao_lidas ON identity.notificacoes(tenant_id, usuario_id) WHERE lida_em IS NULL;

-- Consulta da auditoria por quem agiu (RF50); o filtro por período usa idx_audit_tenant_data
CREATE INDEX idx_audit_tenant_usuario ON identity.audit_logs(tenant_id, usuario_id, created_at DESC);

-- Limpeza diária (LimpezaDeRegistros): apaga por data sem varrer as tabelas inteiras
CREATE INDEX idx_tentativas_data ON identity.tentativas_login(created_at);
CREATE INDEX idx_eventos_processados_data ON identity.eventos_processados(processado_em);
CREATE INDEX idx_refresh_expiracao ON identity.refresh_tokens(expira_em);
CREATE INDEX idx_recuperacoes_expiracao ON identity.recuperacoes_senha(expira_em) WHERE tipo = 'recuperacao';
