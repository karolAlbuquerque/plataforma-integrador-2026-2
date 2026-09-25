import { createHmac } from 'node:crypto'
import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { expect, type APIRequestContext, type Page } from '@playwright/test'

/** Senha dos usuários de teste (docs/usuarios-de-teste.md do infra) e o Mailpit do compose. */
export const SENHA = process.env.SENHA_DE_TESTE ?? 'Plataforma2026'
export const MAILPIT = process.env.MAILPIT_URL ?? 'http://localhost:8025'
export const EMPRESA_A = 'a0000000-0000-4000-8000-00000000000a'

// ------------------------------------------------------------------ segundo fator (RF10)

/**
 * O e2e roda com o segundo fator obrigatório (IDENTITY_EXIGIR_SEGUNDO_FATOR=true), como em
 * produção. O primeiro login de cada usuário cadastra o autenticador; o segredo fica neste arquivo,
 * fora do git, para os logins seguintes. Banco recriado (docker compose down -v) = apague o arquivo.
 * Use um banco só para o e2e: os usuários semeados passam a ter segundo fator.
 */
const ARQUIVO_DE_SEGREDOS = fileURLToPath(new URL('../.segundo-fator.json', import.meta.url))

interface Autenticador {
  segredo: string
  /** Passo de 30 s do último código usado: o identity não aceita o mesmo de novo. */
  ultimoPasso: number
}

function lerSegredos(): Record<string, Autenticador> {
  return existsSync(ARQUIVO_DE_SEGREDOS) ? JSON.parse(readFileSync(ARQUIVO_DE_SEGREDOS, 'utf8')) : {}
}

function guardar(email: string, autenticador: Autenticador) {
  writeFileSync(ARQUIVO_DE_SEGREDOS, JSON.stringify({ ...lerSegredos(), [email]: autenticador }, null, 2))
}

function base32(texto: string) {
  const alfabeto = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'
  let bits = ''
  for (const c of texto.replace(/[\s=]/g, '').toUpperCase()) bits += alfabeto.indexOf(c).toString(2).padStart(5, '0')
  const bytes = []
  for (let i = 0; i + 8 <= bits.length; i += 8) bytes.push(parseInt(bits.slice(i, i + 8), 2))
  return Buffer.from(bytes)
}

/** RFC 6238: HMAC-SHA1, seis dígitos, passos de 30 s — o que o aplicativo do celular calcula. */
export function totp(segredo: string, passo: number) {
  const contador = Buffer.alloc(8)
  contador.writeBigUInt64BE(BigInt(passo))
  const hash = createHmac('sha1', base32(segredo)).update(contador).digest()
  const inicio = hash[hash.length - 1] & 0x0f
  return String((hash.readUInt32BE(inicio) & 0x7fffffff) % 1_000_000).padStart(6, '0')
}

const passoAtual = () => Math.floor(Date.now() / 30_000)

/**
 * Um código que o identity ainda aceita: o identity tolera um passo à frente, então dá para entrar
 * duas vezes no mesmo passo; na terceira, espera o próximo.
 */
async function proximoCodigo(email: string, segredo: string, ultimoPasso: number) {
  for (;;) {
    const passo = Math.max(passoAtual(), ultimoPasso + 1)
    if (passo <= passoAtual() + 1) {
      guardar(email, { segredo, ultimoPasso: passo })
      return totp(segredo, passo)
    }
    await new Promise((resolver) => setTimeout(resolver, (passoAtual() + 1) * 30_000 - Date.now() + 200))
  }
}

export async function codigoDe(email: string) {
  const autenticador = lerSegredos()[email]
  if (!autenticador) throw new Error(`Sem o segredo do autenticador de ${email}: recrie o banco do e2e e apague .segundo-fator.json.`)
  return proximoCodigo(email, autenticador.segredo, autenticador.ultimoPasso)
}

// ------------------------------------------------------------------ entrada

/** Login pela tela, com o segundo fator: cadastro no primeiro acesso, código nos seguintes. */
export async function entrar(page: Page, email: string, senha = SENHA) {
  await page.goto('/')
  await page.getByLabel('E-mail').fill(email)
  await page.getByLabel('Senha', { exact: true }).fill(senha)
  await page.getByRole('button', { name: 'Entrar', exact: true }).click()

  const casca = page.getByRole('navigation', { name: 'Principal' })
  const codigo = page.getByRole('heading', { name: 'Verificação em duas etapas' })
  const cadastro = page.getByRole('heading', { name: 'Proteja a sua conta' })
  await expect(casca.or(codigo).or(cadastro)).toBeVisible()

  if (await cadastro.isVisible()) {
    const segredo = ((await page.getByTestId('segredo-do-segundo-fator').textContent()) ?? '').replace(/\s/g, '')
    await page.getByLabel('Código do aplicativo').fill(await proximoCodigo(email, segredo, 0))
    await page.getByRole('button', { name: 'Ativar e entrar' }).click()
    await expect(page.getByTestId('codigos-de-recuperacao').getByRole('listitem')).toHaveCount(10)
    await page.getByLabel('Guardei os códigos num lugar seguro').check()
    await page.getByRole('button', { name: 'Continuar' }).click()
  } else if (await codigo.isVisible()) {
    await page.getByLabel('Código', { exact: true }).fill(await codigoDe(email))
    await page.getByRole('button', { name: 'Entrar', exact: true }).click()
  }
  await expect(casca).toBeVisible()
}

/** Sessão pela API, para os testes que só precisam de um token ou do id do usuário. */
export async function sessaoPelaApi(request: APIRequestContext, email: string, senha = SENHA) {
  let dados = (await (await request.post('/api/identity/auth/login', { data: { email, senha } })).json()).data
  if (dados.etapa === 'segundo_fator') {
    dados = (await (await request.post('/api/identity/auth/login/segundo-fator',
      { data: { desafio: dados.desafio, codigo: await codigoDe(email) } })).json()).data
  } else if (dados.etapa === 'cadastro_segundo_fator') {
    const { segredo } = (await (await request.post('/api/identity/auth/login/segundo-fator/cadastro',
      { data: { desafio: dados.desafio } })).json()).data
    dados = (await (await request.post('/api/identity/auth/login/segundo-fator/cadastro/confirmar',
      { data: { desafio: dados.desafio, codigo: await proximoCodigo(email, segredo, 0) } })).json()).data
  }
  return dados as { accessToken: string; usuario: { id: string } }
}

/** Navegação interna da casca, sem recarregar a página (como um clique num link). */
export async function irPara(page: Page, caminho: string) {
  await page.evaluate((destino) => {
    window.history.pushState(null, '', destino)
    window.dispatchEvent(new PopStateEvent('popstate'))
  }, caminho)
}

export const menu = (page: Page) => page.getByRole('navigation', { name: 'Principal' })
