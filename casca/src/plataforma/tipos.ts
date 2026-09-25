/** Formatos de contratos/identity.yaml (v0.3.0) do infra-integrador-2026. */

/** Envelope de toda resposta da plataforma (Contrato §8.2). */
export interface Envelope<T> {
  success: boolean
  data: T | null
  message: string | null
  errors: ErroDeCampo[]
}

export interface ErroDeCampo {
  campo: string
  codigo: string
  detalhe: string
}

export interface Pagina<T> {
  itens: T[]
  pagina: number
  tamanho: number
  total: number
}

export interface Usuario {
  id: string
  nome: string
  email: string
  tenantId: string
}

export interface SessaoAberta {
  accessToken: string
  expiraEmSegundos: number
  /** Fim das oito horas da sessão; a renovação não o adia (RF41). */
  sessaoExpiraEm: string
  usuario: Usuario
}

export interface Eu {
  usuario: Usuario
  tenant: { id: string; nome: string | null }
  perfis: string[]
  permissoes: string[]
  equipes: string[]
}

export interface ItemDoSubmenu {
  rota: string
  nome: string
}

export interface ModuloDoMenu {
  codigo: string
  nome: string
  icone: string | null
  urlFrontend: string
  prefixoApi: string
  ordemMenu: number
  disponivel: boolean
  itensSubmenu: ItemDoSubmenu[]
}

// ------------------------------------------------------------------ senha e sessões

export interface LinkVerificado {
  tipo: 'convite' | 'recuperacao'
  nome: string
  email: string
  expiraEm: string
}

export interface SessaoAtiva {
  id: string
  ip: string | null
  navegador: string | null
  iniciadaEm: string
  ultimaRenovacaoEm: string
  expiraEm: string
  atual: boolean
}

// ------------------------------------------------------------------ usuários

export type Situacao = 'ativo' | 'convite_pendente' | 'convite_expirado' | 'inativo'

export interface PerfilDoUsuario {
  id: string
  nome: string
  rotulo: string
  sistema: boolean
}

export interface EquipeDoUsuario {
  id: string
  nome: string
  lider: boolean
}

export interface UsuarioDaLista {
  id: string
  nome: string
  email: string
  telefone: string | null
  situacao: Situacao
  perfis: PerfilDoUsuario[]
  ultimoLoginEm: string | null
  criadoEm: string
}

export interface UsuarioDetalhe extends UsuarioDaLista {
  equipes: EquipeDoUsuario[]
  conviteExpiraEm: string | null
}

export interface UsuarioSalvo {
  usuario: UsuarioDetalhe
  /** Nulo quando a operação não envolveu convite; false quando o SMTP falhou. */
  conviteEnviado: boolean | null
}

export interface Conta {
  id: string
  nome: string
  email: string
  telefone: string | null
  tenant: { id: string; nome: string | null }
  perfis: PerfilDoUsuario[]
  equipes: EquipeDoUsuario[]
  ultimoLoginEm: string | null
  criadoEm: string
}

// ------------------------------------------------------------------ perfis e permissões

export interface PermissaoDoCatalogo {
  codigo: string
  recurso: string
  acao: string
  descricao: string
}

export interface PermissoesDoModulo {
  modulo: string
  nome: string
  permissoes: PermissaoDoCatalogo[]
}

export interface PerfilResumo {
  id: string
  nome: string
  rotulo: string
  descricao: string | null
  sistema: boolean
  totalUsuarios: number
  totalPermissoes: number
}

export interface PerfilDetalhe {
  id: string
  nome: string
  rotulo: string
  descricao: string | null
  sistema: boolean
  totalUsuarios: number
  permissoes: string[]
}

// ------------------------------------------------------------------ equipes

export interface EquipeResumo {
  id: string
  nome: string
  totalMembros: number
  lideres: string[]
}

export interface MembroDaEquipe {
  id: string
  nome: string
  lider: boolean
}

export interface EquipeDetalhe {
  id: string
  nome: string
  membros: MembroDaEquipe[]
}

export type Tema = 'claro' | 'escuro'

export type NivelDeAviso = 'sucesso' | 'erro' | 'info'

// ---------------------------------------------------------------- onda 3

export interface Notificacao {
  id: string
  categoria: string
  titulo: string
  texto: string | null
  /** Nulo nas notificações gravadas antes da onda 3. */
  moduloOrigem: string | null
  /** Relativa ao front do módulo de origem (Contrato §12.2). */
  rota: string | null
  lida: boolean
  criadaEm: string
}

export interface RegistroDeAuditoria {
  id: string
  ocorridoEm: string
  usuario: { id: string; nome: string } | null
  ip: string | null
  acao: string
  entidade: string
  entidadeId: string | null
  valorAnterior: Record<string, unknown> | null
  valorNovo: Record<string, unknown> | null
}
