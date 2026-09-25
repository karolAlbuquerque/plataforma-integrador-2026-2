import { useEffect, useState } from 'react'
import type { Situacao } from './tipos'

const DATA_HORA = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' })
const RELATIVO = new Intl.RelativeTimeFormat('pt-BR', { numeric: 'auto' })

export function dataHora(iso: string | null | undefined) {
  return iso ? DATA_HORA.format(new Date(iso)) : null
}

/** "há 5 minutos", "ontem", "há 3 dias" — para último acesso e atividade de sessão. */
export function haQuanto(iso: string | null | undefined) {
  if (!iso) return null
  const segundos = Math.round((new Date(iso).getTime() - Date.now()) / 1000)
  const unidades: [Intl.RelativeTimeFormatUnit, number][] = [
    ['year', 31_536_000], ['month', 2_592_000], ['day', 86_400], ['hour', 3_600], ['minute', 60],
  ]
  for (const [unidade, tamanho] of unidades) {
    if (Math.abs(segundos) >= tamanho) return RELATIVO.format(Math.round(segundos / tamanho), unidade)
  }
  return 'agora'
}

export const SITUACOES: Record<Situacao, string> = {
  ativo: 'Ativo',
  convite_pendente: 'Convite pendente',
  convite_expirado: 'Convite expirado',
  inativo: 'Inativo',
  anonimizado: 'Anonimizado',
}

/** Sem acento e em minúsculas, para buscas locais. */
export function normalizar(texto: string) {
  return texto.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase()
}

/** Valor que só muda depois de o usuário parar de digitar. */
export function useAtraso<T>(valor: T, espera = 300): T {
  const [atrasado, setAtrasado] = useState(valor)
  useEffect(() => {
    const temporizador = window.setTimeout(() => setAtrasado(valor), espera)
    return () => window.clearTimeout(temporizador)
  }, [valor, espera])
  return atrasado
}

/**
 * A mesma regra do identity (PoliticaDeSenha): 8 caracteres ou mais, com letra e número, até
 * 72 bytes. Aqui serve só para a tela orientar antes de enviar — quem decide é o servidor.
 */
export function regrasDaSenha(senha: string) {
  return {
    tamanho: [...senha].length >= 8,
    letraENumero: /\p{L}/u.test(senha) && /\p{Nd}/u.test(senha),
    limite: new TextEncoder().encode(senha).length <= 72,
  }
}

export function senhaAceitavel(senha: string) {
  const regras = regrasDaSenha(senha)
  return regras.tamanho && regras.letraENumero && regras.limite
}
