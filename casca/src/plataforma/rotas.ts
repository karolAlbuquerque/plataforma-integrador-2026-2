import { useSyncExternalStore } from 'react'

/**
 * Duas rotas só, sem biblioteca: "/" é o início e "/app/{codigo}/..." é a área de um módulo.
 * A parte depois do código é a rota interna do módulo, repassada ao iframe.
 */
export type Rota =
  | { tipo: 'inicio' }
  | { tipo: 'modulo'; codigo: string; subrota: string; versao: number }

const EVENTO = 'plataforma:rota'
const ROTA_DE_MODULO = /^\/app\/([a-z]+)(\/.*)?$/

/** Sobe a cada navegação feita pela casca; a navegação feita de dentro do módulo não mexe nele. */
let versao = 0
let rotaAtual = ler()

function ler(): Rota {
  const caminho = window.location.pathname
  const modulo = ROTA_DE_MODULO.exec(caminho)
  if (modulo) {
    return { tipo: 'modulo', codigo: modulo[1], subrota: (modulo[2] ?? '/') + window.location.search, versao }
  }
  return { tipo: 'inicio' }
}

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

/** Navegação da casca: menu, cartões do início. Recarrega o iframe na rota pedida. */
export function navegar(caminho: string) {
  if (caminho === window.location.pathname + window.location.search) return
  versao++
  window.history.pushState(null, '', caminho)
  atualizar()
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
