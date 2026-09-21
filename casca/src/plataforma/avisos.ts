import { useSyncExternalStore } from 'react'
import type { NivelDeAviso } from './tipos'

/** Avisos rápidos no canto da tela — os da casca e os pedidos por modulo:notificar. */
export interface Aviso {
  id: number
  nivel: NivelDeAviso
  texto: string
}

const DURACAO = 5_000
const MAXIMO_NA_TELA = 4

let avisos: Aviso[] = []
let proximoId = 1
const ouvintes = new Set<() => void>()

function publicar(novos: Aviso[]) {
  avisos = novos
  ouvintes.forEach((ouvinte) => ouvinte())
}

export function avisar(nivel: NivelDeAviso, texto: string) {
  const aviso = { id: proximoId++, nivel, texto: texto.slice(0, 300) }
  publicar([...avisos, aviso].slice(-MAXIMO_NA_TELA))
  window.setTimeout(() => dispensar(aviso.id), nivel === 'erro' ? DURACAO * 2 : DURACAO)
}

export function dispensar(id: number) {
  publicar(avisos.filter((aviso) => aviso.id !== id))
}

export function useAvisos(): Aviso[] {
  return useSyncExternalStore(
    (aoMudar) => {
      ouvintes.add(aoMudar)
      return () => ouvintes.delete(aoMudar)
    },
    () => avisos,
  )
}
