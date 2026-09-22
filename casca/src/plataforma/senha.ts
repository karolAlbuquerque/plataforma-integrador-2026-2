import { ErroDaApi } from './api'
import { lerEnvelope } from './sessao'
import type { LinkVerificado } from './tipos'

/**
 * Rotas públicas de senha (contratos/identity.yaml, tag "senha"): quem chega ainda não tem sessão.
 * O token do link vai no corpo, nunca na URL da requisição.
 */
async function postarSemSessao<T>(rota: string, corpo: unknown): Promise<{ dados: T | null; mensagem: string | null }> {
  let resposta: Response
  try {
    resposta = await fetch(`/api/identity/auth/senha/${rota}`, {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(corpo),
    })
  } catch {
    throw new ErroDaApi(0, null)
  }
  const envelope = await lerEnvelope<T>(resposta)
  if (!resposta.ok || !envelope?.success) throw new ErroDaApi(resposta.status, envelope)
  return { dados: envelope.data, mensagem: envelope.message }
}

export const pedirRecuperacao = (email: string) => postarSemSessao<null>('recuperar', { email })

export async function verificarLink(token: string): Promise<LinkVerificado> {
  const { dados } = await postarSemSessao<LinkVerificado>('verificar', { token })
  return dados as LinkVerificado
}

export const definirSenha = (token: string, novaSenha: string) => postarSemSessao<null>('definir', { token, novaSenha })
