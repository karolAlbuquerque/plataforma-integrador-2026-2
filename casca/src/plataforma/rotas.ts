import { useSyncExternalStore } from 'react'

/**
 * Rotas da casca, sem biblioteca. "/app/{codigo}/..." é a área de um módulo: a parte depois do
 * código é a rota interna dele, repassada ao iframe. As de administração e a conta são telas da
 * própria casca; esqueci-senha e definir-senha são públicas.
 */
export type Rota =
  | { tipo: 'inicio' }
  | { tipo: 'modulo'; codigo: string; subrota: string; versao: number }
  | { tipo: 'conta' }
  | { tipo: 'usuarios' }
  | { tipo: 'perfis' }
  | { tipo: 'perfil'; id: string }
  | { tipo: 'equipes' }
  | { tipo: 'auditoria' }
  | { tipo: 'esqueci-senha' }
  | { tipo: 'definir-senha' }
  | { tipo: 'nao-encontrada' }

const EVENTO = 'plataforma:rota'
const ROTA_DE_MODULO = /^\/app\/([a-z]+)(\/.*)?$/
const ROTA_DE_PERFIL = /^\/admin\/perfis\/([0-9a-f-]{36})$/i

const FIXAS: Record<string, Rota> = {
  '/': { tipo: 'inicio' },
  '/conta': { tipo: 'conta' },
  '/admin/usuarios': { tipo: 'usuarios' },
  '/admin/perfis': { tipo: 'perfis' },
  '/admin/equipes': { tipo: 'equipes' },
  '/admin/auditoria': { tipo: 'auditoria' },
  '/esqueci-senha': { tipo: 'esqueci-senha' },
  '/definir-senha': { tipo: 'definir-senha' },
}

/** Sobe a cada navegação feita pela casca; a navegação feita de dentro do módulo não mexe nele. */
let versao = 0
let rotaAtual = ler()

function ler(): Rota {
  const caminho = window.location.pathname
  const modulo = ROTA_DE_MODULO.exec(caminho)
  if (modulo) {
    return { tipo: 'modulo', codigo: modulo[1], subrota: (modulo[2] ?? '/') + window.location.search, versao }
  }
  const semBarra = caminho.length > 1 ? caminho.replace(/\/+$/, '') : caminho
  const perfil = ROTA_DE_PERFIL.exec(semBarra)
  if (perfil) return { tipo: 'perfil', id: perfil[1].toLowerCase() }
  return FIXAS[semBarra] ?? { tipo: 'nao-encontrada' }
}

export const ehPublica = (rota: Rota) => rota.tipo === 'esqueci-senha' || rota.tipo === 'definir-senha'

function atualizar() {
  rotaAtual = ler()
  window.dispatchEvent(new Event(EVENTO))
}

window.addEventListener('popstate', () => {
  versao++
  atualizar()
})

export function caminhoDoModulo(codigo: string, subrota = '/') {
  return `/app/${codigo}${subrota.startsWith('/') ? subrota : `/${subrota}`}`
}

/** Navegação da casca: menu, cartões, botões. Recarrega o iframe do módulo na rota pedida. */
export function navegar(caminho: string) {
  if (caminho === window.location.pathname + window.location.search) return
  versao++
  window.history.pushState(null, '', caminho)
  atualizar()
  window.scrollTo(0, 0)
}

/**
 * modulo:navegar (Contrato §12.2): o módulo mudou de tela por dentro e avisa, para a URL da casca
 * acompanhar — recarregar a página e o botão voltar funcionam. O iframe não é recarregado.
 */
export function refletirRotaDoModulo(codigo: string, rota: string) {
  if (!/^\/(?!\/)[\w\-./~%?=&]*$/.test(rota) || rota.length > 500) return
  const caminho = caminhoDoModulo(codigo, rota)
  if (caminho === window.location.pathname + window.location.search) return
  window.history.replaceState(null, '', caminho)
  atualizar()
}

export function useRota(): Rota {
  return useSyncExternalStore(
    (aoMudar) => {
      window.addEventListener(EVENTO, aoMudar)
      return () => window.removeEventListener(EVENTO, aoMudar)
    },
    () => rotaAtual,
  )
}
