import { lerEnvelope, renovar, sessaoAtual } from './sessao'
import type { Envelope } from './tipos'

export class ErroDaApi extends Error {
  readonly status: number
  readonly envelope: Envelope<unknown> | null

  constructor(status: number, envelope: Envelope<unknown> | null) {
    super(envelope?.message ?? mensagemPadrao(status))
    this.status = status
    this.envelope = envelope
  }

  /** errors[] do envelope por campo, para mostrar cada mensagem junto do seu campo. */
  get porCampo(): Record<string, string> {
    const campos: Record<string, string> = {}
    for (const erro of this.envelope?.errors ?? []) {
      campos[erro.campo] ??= erro.detalhe
    }
    return campos
  }

  get codigo(): string | undefined {
    return this.envelope?.errors?.[0]?.codigo
  }
}

function mensagemPadrao(status: number) {
  if (status === 0) return 'Sem conexão com a plataforma. Confira a rede e tente de novo.'
  if (status === 403) return 'Sem permissão para esta operação.'
  if (status >= 500) return 'A plataforma não respondeu. Tente de novo em instantes.'
  return `A requisição falhou (HTTP ${status}).`
}

/** Mensagem para a tela, venha o erro da API ou da rede. */
export function mensagemDe(erro: unknown): string {
  if (erro instanceof ErroDaApi) return erro.message
  return mensagemPadrao(0)
}

/** Chamada autenticada às APIs pelo gateway. Um 401 renova a sessão e tenta mais uma vez. */
export async function chamar<T>(caminho: string, opcoes: RequestInit = {}, jaRenovou = false): Promise<T> {
  const sessao = sessaoAtual()
  const cabecalhos = new Headers(opcoes.headers)
  if (sessao) cabecalhos.set('Authorization', `Bearer ${sessao.token}`)
  if (opcoes.body && !cabecalhos.has('Content-Type')) cabecalhos.set('Content-Type', 'application/json')

  let resposta: Response
  try {
    resposta = await fetch(caminho, { ...opcoes, headers: cabecalhos, credentials: 'same-origin' })
  } catch {
    throw new ErroDaApi(0, null)
  }
  if (resposta.status === 401 && sessao && !jaRenovou) {
    const renovada = await renovar()
    if (renovada) return chamar<T>(caminho, opcoes, true)
  }
  const envelope = await lerEnvelope<T>(resposta)
  if (!resposta.ok || !envelope?.success) throw new ErroDaApi(resposta.status, envelope)
  return envelope.data as T
}

/** Resposta de sucesso sem dado (ex.: exclusão): devolve a mensagem que a tela mostra. */
async function chamarComMensagem(caminho: string, opcoes: RequestInit): Promise<string | null> {
  const sessao = sessaoAtual()
  const cabecalhos = new Headers(opcoes.headers)
  if (sessao) cabecalhos.set('Authorization', `Bearer ${sessao.token}`)
  if (opcoes.body) cabecalhos.set('Content-Type', 'application/json')
  let resposta: Response
  try {
    resposta = await fetch(caminho, { ...opcoes, headers: cabecalhos, credentials: 'same-origin' })
  } catch {
    throw new ErroDaApi(0, null)
  }
  if (resposta.status === 401 && sessao && (await renovar())) return chamarComMensagem(caminho, opcoes)
  const envelope = await lerEnvelope<unknown>(resposta)
  if (!resposta.ok || !envelope?.success) throw new ErroDaApi(resposta.status, envelope)
  return envelope.message
}

/**
 * Arquivo (Contrato §8.2): sucesso fora do envelope, erro dentro dele. O token vai no cabeçalho —
 * nunca na URL, que fica em log e no histórico — e o arquivo é salvo a partir do blob recebido.
 */
async function baixar(caminho: string, nomePadrao: string, jaRenovou = false): Promise<void> {
  const sessao = sessaoAtual()
  const cabecalhos = new Headers()
  if (sessao) cabecalhos.set('Authorization', `Bearer ${sessao.token}`)
  let resposta: Response
  try {
    resposta = await fetch(caminho, { headers: cabecalhos, credentials: 'same-origin' })
  } catch {
    throw new ErroDaApi(0, null)
  }
  if (resposta.status === 401 && sessao && !jaRenovou && (await renovar())) return baixar(caminho, nomePadrao, true)
  if (!resposta.ok) throw new ErroDaApi(resposta.status, await lerEnvelope<unknown>(resposta))

  const nome = /filename="([^"]+)"/.exec(resposta.headers.get('Content-Disposition') ?? '')?.[1] ?? nomePadrao
  const endereco = URL.createObjectURL(await resposta.blob())
  const link = document.createElement('a')
  link.href = endereco
  link.download = nome
  document.body.append(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(endereco), 1_000)
}

export const api = {
  get: <T>(caminho: string) => chamar<T>(caminho),
  post: <T>(caminho: string, corpo?: unknown) =>
    chamar<T>(caminho, { method: 'POST', body: corpo === undefined ? undefined : JSON.stringify(corpo) }),
  put: <T>(caminho: string, corpo: unknown) => chamar<T>(caminho, { method: 'PUT', body: JSON.stringify(corpo) }),
  /** Operações cuja resposta é só a mensagem (excluir, encerrar sessão, trocar senha). */
  acao: (metodo: 'POST' | 'DELETE', caminho: string, corpo?: unknown) =>
    chamarComMensagem(caminho, { method: metodo, body: corpo === undefined ? undefined : JSON.stringify(corpo) }),
  baixar,
}

/** Monta ?a=1&b=2 sem os parâmetros vazios. */
export function consulta(parametros: Record<string, string | number | null | undefined>) {
  const busca = new URLSearchParams()
  for (const [chave, valor] of Object.entries(parametros)) {
    if (valor !== null && valor !== undefined && valor !== '') busca.set(chave, String(valor))
  }
  const texto = busca.toString()
  return texto ? `?${texto}` : ''
}
