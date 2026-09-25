/**
 * Rótulos das ações e entidades gravadas pelo identity (AcaoAuditada e EntidadeAuditada em
 * contratos/identity.yaml). Código desconhecido aparece como veio: a lista cresce no servidor antes
 * de crescer aqui.
 */

export const ACOES: Record<string, string> = {
  login: 'Entrou',
  login_falhou: 'Falha ao entrar',
  login_bloqueado: 'Entrada bloqueada',
  logout: 'Saiu',
  refresh_reutilizado: 'Renovação reaproveitada',
  encerrar_sessao: 'Encerrou uma sessão',
  encerrar_outras_sessoes: 'Encerrou as outras sessões',
  token_servico: 'Token de serviço emitido',
  token_servico_recusado: 'Token de serviço recusado',
  acesso_negado: 'Acesso negado',
  recuperacao_solicitada: 'Pediu recuperação de senha',
  recuperacao_ignorada: 'Recuperação ignorada',
  definir_senha: 'Definiu a senha',
  trocar_senha: 'Trocou a senha',
  trocar_senha_recusada: 'Troca de senha recusada',
  criar: 'Criou',
  editar: 'Editou',
  excluir: 'Excluiu',
  alterar: 'Alterou',
  convidar: 'Convidou',
  desativar: 'Desativou',
  reativar: 'Reativou',
  exportar: 'Exportou',
}

export const ENTIDADES: Record<string, string> = {
  sessao: 'Sessão',
  credencial_servico: 'Credencial de serviço',
  senha: 'Senha',
  usuario: 'Usuário',
  usuario_perfis: 'Perfis do usuário',
  usuario_equipes: 'Equipes do usuário',
  perfil: 'Perfil',
  perfil_permissoes: 'Permissões do perfil',
  equipe: 'Equipe',
  equipe_membros: 'Membros da equipe',
  rota: 'Rota da API',
  auditoria: 'Auditoria',
}

type Tom = 'red' | 'green' | 'orange' | 'indigo' | 'gray'

/** Cor do selo: recusa em vermelho, criação em verde, perda de acesso em laranja. */
export function tomDaAcao(acao: string): Tom {
  if (/falhou|bloqueado|recusad|negado|reutilizado/.test(acao)) return 'red'
  if (acao === 'criar' || acao === 'convidar' || acao === 'reativar') return 'green'
  if (acao === 'excluir' || acao === 'desativar' || acao.startsWith('encerrar')) return 'orange'
  if (acao === 'alterar' || acao === 'editar' || acao.includes('senha')) return 'indigo'
  return 'gray'
}

export const rotuloDaAcao = (acao: string) => ACOES[acao] ?? acao
export const rotuloDaEntidade = (entidade: string) => ENTIDADES[entidade] ?? entidade
