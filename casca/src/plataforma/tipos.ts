/** Formatos de contratos/identity.yaml (v0.4.0) do infra-integrador-2026. */

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
  /** Guardado na conta (RF40). Ausente num identity anterior à onda 4. */
  tema?: PreferenciaDeTema
}

export interface SessaoAberta {
  accessToken: string
  expiraEmSegundos: number
  /** Fim das oito horas da sessão; a renovação não o adia (RF41). */
  sessaoExpiraEm: string
  usuario: Usuario
  /** Só ao concluir o cadastro do segundo fator: a única vez em que aparecem. */
  codigosRecuperacao?: string[]
}

/** Senha certa, falta o segundo fator (RF10): sem cookie e sem access token ainda. */
export interface DesafioDeSegundoFator {
  etapa: 'segundo_fator' | 'cadastro_segundo_fator'
  desafio: string
  desafioExpiraEm: string
}

export interface CadastroDeSegundoFator {
  /** Base32, para digitar no aplicativo quando não der para ler o QR Code. */
  segredo: string
  /** Conteúdo do QR Code (otpauth://totp/...). */
  uri: string
}

export interface SituacaoDoSegundoFator {
  ativo: boolean
  ativadoEm: string | null
  codigosRestantes: number
  obrigatorio: boolean
}

/** Formato comum da busca global (Contrato §8.6). */
export interface ItemDaBusca {
  id: string
  titulo: string
  subtitulo: string | null
  /** Relativa ao urlFrontend do módulo; no identity, um caminho da própria casca. */
  rota: string
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
  /** Responde GET {prefixoApi}/busca e entra na busca global. Ausente num identity anterior à onda 4. */
  busca?: boolean
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

export type Situacao = 'ativo' | 'convite_pendente' | 'convite_expirado' | 'inativo' | 'anonimizado'

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
  segundoFatorAtivo?: boolean
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
  tema?: PreferenciaDeTema
  segundoFatorAtivo?: boolean
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

/** O tema aplicado na tela. */
export type Tema = 'claro' | 'escuro'

/** A escolha do usuário: "sistema" segue o prefers-color-scheme do navegador. */
export type PreferenciaDeTema = Tema | 'sistema'

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
