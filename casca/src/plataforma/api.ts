import { lerEnvelope, renovar, sessaoAtual } from './sessao'
import type { Envelope } from './tipos'

export class ErroDaApi extends Error {
  readonly status: number
  readonly envelope: Envelope<unknown> | null

  constructor(status: number, envelope: Envelope<unknown> | null) {
    super(envelope?.message ?? `A requisição falhou (HTTP ${status}).`)
    this.status = status
    this.envelope = envelope
  }
}

/** Chamada autenticada às APIs pelo gateway. Um 401 renova a sessão e tenta mais uma vez. */
export async function chamar<T>(caminho: string, opcoes: RequestInit = {}, jaRenovou = false): Promise<T> {
  const sessao = sessaoAtual()
  const cabecalhos = new Headers(opcoes.headers)
  if (sessao) cabecalhos.set('Authorization', `Bearer ${sessao.token}`)
  if (opcoes.body && !cabecalhos.has('Content-Type')) cabecalhos.set('Content-Type', 'application/json')

  const resposta = await fetch(caminho, { ...opcoes, headers: cabecalhos })
  if (resposta.status === 401 && sessao && !jaRenovou) {
    const renovada = await renovar()
    if (renovada) return chamar<T>(caminho, opcoes, true)
  }
  const envelope = await lerEnvelope<T>(resposta)
  if (!resposta.ok || !envelope?.success) throw new ErroDaApi(resposta.status, envelope)
  return envelope.data as T
}
