import { readFile } from 'node:fs/promises'
import { expect, test, type APIRequestContext } from '@playwright/test'
import { MAILPIT, codigoDe, entrar, irPara, sessaoPelaApi } from './apoio'

/**
 * Onda 4 na casca: verificação em duas etapas (RF10), tema na conta (RF40), busca global (RF55)
 * e dados pessoais (RF56). Precisa do compose com IDENTITY_EXIGIR_SEGUNDO_FATOR=true.
 */

const ADMIN = 'administrador@empresa-a.dev'

/** Usuário novo, com senha já definida pelo link do convite — rodar de novo não esbarra no anterior. */
async function usuarioNovo(request: APIRequestContext, prefixo: string, nome: string) {
  const email = `${prefixo}-${Date.now()}@teste.dev`
  const senha = `Senha${Date.now()}x`
  const admin = await sessaoPelaApi(request, ADMIN)
  const autorizacao = { Authorization: `Bearer ${admin.accessToken}` }
  const perfis = (await (await request.get('/api/identity/perfis?tamanho=100', { headers: autorizacao })).json()).data.itens
  const vendedor = perfis.find((p: { nome: string }) => p.nome === 'VENDEDOR').id
  const criado = (await (await request.post('/api/identity/usuarios',
    { headers: autorizacao, data: { nome, email, perfis: [vendedor], equipes: [] } })).json()).data
  await expect.poll(() => tokenDoConvite(email), { timeout: 10_000 }).not.toBeNull()
  const definida = await request.post('/api/identity/auth/senha/definir', { data: { token: await tokenDoConvite(email), novaSenha: senha } })
  expect(definida.ok()).toBe(true)
  return { email, senha, id: criado.usuario.id as string, autorizacao }
}

async function tokenDoConvite(email: string): Promise<string | null> {
  const busca = await (await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:${email}`)}`)).json()
  const id = busca.messages?.[0]?.ID
  if (!id) return null
  const mensagem = await (await fetch(`${MAILPIT}/api/v1/message/${id}`)).json()
  return /definir-senha#token=([A-Za-z0-9_-]+)/.exec(mensagem.Text)?.[1] ?? null
}

async function assuntosPara(email: string): Promise<string[]> {
  const busca = await (await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:${email}`)}`)).json()
  return (busca.messages ?? []).map((m: { Subject: string }) => m.Subject)
}

test('primeiro acesso cadastra o autenticador; sem o celular, um código de recuperação entra uma vez', async ({ page, request }) => {
  test.setTimeout(120_000)
  const { email, senha } = await usuarioNovo(request, 'e2e-2fa', 'Pessoa do Autenticador')

  await page.goto('/')
  await page.getByLabel('E-mail').fill(email)
  await page.getByLabel('Senha', { exact: true }).fill(senha)
  await page.getByRole('button', { name: 'Entrar', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Proteja a sua conta' })).toBeVisible()
  await expect(page.getByRole('img', { name: 'QR Code para o aplicativo autenticador' })).toBeVisible()

  // entrar() lê o segredo da tela, calcula o código como o celular e passa pelos códigos de recuperação
  await page.goto('/')
  await entrar(page, email, senha)
  await expect(page.getByRole('heading', { name: 'Olá, Pessoa' })).toBeVisible()
  await expect.poll(() => assuntosPara(email)).toContain('Verificação em duas etapas ativada na sua conta')

  // Os códigos só aparecem uma vez: gera novos em Minha conta, com senha e código
  await irPara(page, '/conta')
  const painel = page.getByRole('region', { name: 'Verificação em duas etapas' })
  await expect(painel.getByText('Ativa')).toBeVisible()
  await painel.getByRole('button', { name: 'Gerar novos códigos' }).click()
  const modal = page.getByRole('dialog', { name: 'Gerar novos códigos de recuperação' })
  await modal.getByLabel('Senha').fill(senha)
  await modal.getByLabel('Código do aplicativo atual').fill(await codigoDe(email))
  await modal.getByRole('button', { name: 'Continuar' }).click()
  const lista = page.getByTestId('codigos-de-recuperacao').getByRole('listitem')
  await expect(lista).toHaveCount(10)
  const codigos = await lista.allTextContents()
  await page.getByRole('button', { name: 'Guardei os códigos' }).click()

  // Perdeu o celular: entra com um código de recuperação, que depois não vale mais
  for (const esperado of ['entra', 'recusa']) {
    const outro = await (await page.context().browser()!.newContext()).newPage()
    await outro.goto('/')
    await outro.getByLabel('E-mail').fill(email)
    await outro.getByLabel('Senha', { exact: true }).fill(senha)
    await outro.getByRole('button', { name: 'Entrar', exact: true }).click()
    await outro.getByRole('button', { name: /usar um código de recuperação/ }).click()
    await outro.getByLabel('Código de recuperação').fill(codigos[0])
    await outro.getByRole('button', { name: 'Entrar', exact: true }).click()
    if (esperado === 'entra') await expect(outro.getByRole('navigation', { name: 'Principal' })).toBeVisible()
    else await expect(outro.getByRole('alert')).toHaveText('Código inválido.')
    await outro.close()
  }
  await expect.poll(() => assuntosPara(email)).toContain('Um código de recuperação foi usado na sua conta')
})

test('o tema escolhido fica na conta e vale em outro navegador', async ({ page, browser }) => {
  test.setTimeout(90_000)
  const email = 'pre-vendas@empresa-a.dev'
  await entrar(page, email)
  await irPara(page, '/conta')
  await page.getByRole('radio', { name: 'Escuro' }).click()
  await expect(page.locator('html')).toHaveAttribute('data-tema', 'escuro')

  const outro = await (await browser.newContext({ colorScheme: 'light' })).newPage()
  await entrar(outro, email)
  await expect(outro.locator('html')).toHaveAttribute('data-tema', 'escuro')

  // "Sistema" acompanha o navegador — e deixa a conta como estava para as próximas rodadas
  await irPara(outro, '/conta')
  await outro.getByRole('radio', { name: 'Sistema' }).click()
  await expect(outro.locator('html')).toHaveAttribute('data-tema', 'claro')
  await outro.emulateMedia({ colorScheme: 'dark' })
  await expect(outro.locator('html')).toHaveAttribute('data-tema', 'escuro')
})

test('busca global encontra usuários e registros do módulo e abre cada um', async ({ page, request }) => {
  const admin = await sessaoPelaApi(request, ADMIN)
  const nome = `Roteador R-${Date.now()}`
  const criado = await request.post('/api/exemplo/itens', { headers: { Authorization: `Bearer ${admin.accessToken}` }, data: { nome } })
  expect(criado.status()).toBe(201)

  await entrar(page, ADMIN)
  await page.keyboard.press('Control+k')
  const busca = page.getByRole('dialog', { name: 'Buscar' })
  await busca.getByRole('combobox').fill(nome)
  const item = busca.getByRole('option', { name: nome })
  await expect(item).toBeVisible()
  await expect(busca.getByText('Exemplo', { exact: true })).toBeVisible()
  await item.click()
  await expect(page).toHaveURL(/\/app\/exemplo\/\?item=[0-9a-f-]{36}$/)

  await page.keyboard.press('Control+k')
  await busca.getByRole('combobox').fill('Gestor da Empresa')
  await busca.getByRole('option', { name: /Gestor da Empresa A/ }).click()
  await expect(page.getByRole('dialog', { name: 'Detalhe do usuário' }).getByRole('heading', { name: 'Gestor da Empresa A' })).toBeVisible()
})

test('dados pessoais: exportar e anonimizar quem foi desativado', async ({ page, request }) => {
  test.setTimeout(90_000)
  const { email, id, autorizacao } = await usuarioNovo(request, 'e2e-lgpd', 'Titular dos Dados')
  expect((await request.post(`/api/identity/usuarios/${id}/desativar`, { headers: autorizacao })).ok()).toBe(true)

  await entrar(page, ADMIN)
  await irPara(page, `/admin/usuarios?usuario=${id}`)
  const detalhe = page.getByRole('dialog', { name: 'Detalhe do usuário' })
  await expect(detalhe.getByRole('heading', { name: 'Titular dos Dados' })).toBeVisible()

  const [arquivo] = await Promise.all([page.waitForEvent('download'), detalhe.getByRole('button', { name: 'Exportar dados pessoais' }).click()])
  const exportado = JSON.parse(await readFile(await arquivo.path(), 'utf8'))
  expect(exportado.cadastro.email).toBe(email)

  await detalhe.getByRole('button', { name: 'Anonimizar' }).click()
  const confirmacao = page.getByRole('dialog', { name: 'Anonimizar Titular dos Dados?' })
  await expect(confirmacao.getByRole('button', { name: 'Anonimizar' })).toBeDisabled()
  await confirmacao.getByLabel(`Digite ${email} para confirmar`).fill(email)
  await confirmacao.getByRole('button', { name: 'Anonimizar' }).click()
  await expect(detalhe.getByRole('heading', { name: 'Usuário anonimizado' })).toBeVisible()
  await expect(detalhe.getByText('Anonimizado').first()).toBeVisible()
})
