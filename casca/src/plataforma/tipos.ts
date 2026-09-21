/** Formatos de contratos/identity.yaml do infra-integrador-2026. */

/** Envelope de toda resposta da plataforma (Contrato §8.2). */
export interface Envelope<T> {
  success: boolean
  data: T | null
  message: string | null
  errors: { campo: string; codigo: string; detalhe: string }[]
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

export type Tema = 'claro' | 'escuro'

export type NivelDeAviso = 'sucesso' | 'erro' | 'info'
