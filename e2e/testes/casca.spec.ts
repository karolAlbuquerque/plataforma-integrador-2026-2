import { expect, test, type Page } from '@playwright/test'

/**
 * Caminho login → menu → módulo e as telas de administração (Requisito RNF08), contra o compose.
 * Usuários de teste e senha: docs/usuarios-de-teste.md do infra-integrador-2026 (só em dev).
 */
const SENHA = process.env.SENHA_DE_TESTE ?? 'Plataforma2026'
const MAILPIT = process.env.MAILPIT_URL ?? 'http://localhost:8025'

async function entrar(page: Page, email: string, senha = SENHA) {
  await page.goto('/')
  await page.getByLabel('E-mail').fill(email)
  await page.getByLabel('Senha', { exact: true }).fill(senha)
  await page.getByRole('button', { name: 'Entrar', exact: true }).click()
  await expect(page.getByRole('navigation', { name: 'Principal' })).toBeVisible()
}

/** Navegação interna da casca, sem recarregar a página (como um clique num link). */
async function irPara(page: Page, caminho: string) {
  await page.evaluate((destino) => {
    window.history.pushState(null, '', destino)
    window.dispatchEvent(new PopStateEvent('popstate'))
  }, caminho)
}

const menu = (page: Page) => page.getByRole('navigation', { name: 'Principal' })

test('login, menu e módulo embutido recebendo a sessão', async ({ page }) => {
  await entrar(page, 'administrador@empresa-a.dev')
  await expect(page.getByRole('heading', { name: 'Olá, Administrador' })).toBeVisible()

  await menu(page).getByRole('link', { name: 'Exemplo' }).click()
  await expect(page).toHaveURL(/\/app\/exemplo\/$/)
  // O front do módulo só mostra os itens depois de receber plataforma:sessao da casca
  await expect(page.frameLocator('iframe[title="Exemplo"]').getByRole('heading', { name: 'Itens' })).toBeVisible()
})

test('recarregar a página mantém a sessão pelo cookie', async ({ page }) => {
  await entrar(page, 'gestor@empresa-a.dev')
  await page.reload()
  await expect(menu(page)).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Olá, Gestor' })).toBeVisible()
})

test('o menu segue as permissões e a tela protegida responde sem acesso', async ({ page }) => {
  await entrar(page, 'vendedor@empresa-a.dev')
  await expect(menu(page).getByRole('link', { name: 'Início' })).toBeVisible()
  await expect(menu(page).getByRole('link', { name: 'Exemplo' })).toHaveCount(0)
  await expect(menu(page).getByRole('link', { name: 'Usuários' })).toHaveCount(0)

  await irPara(page, '/admin/usuarios')
  await expect(page.getByText('Você não tem acesso a esta tela')).toBeVisible()
})

test('administração de usuários: lista, busca e detalhe', async ({ page }) => {
  await entrar(page, 'administrador@empresa-a.dev')
  await menu(page).getByRole('link', { name: 'Usuários' }).click()
  await expect(page.getByRole('heading', { name: 'Usuários', level: 1 })).toBeVisible()

  await page.getByLabel('Buscar por nome ou e-mail').fill('gestor@empresa-a')
  await expect(page.getByRole('button', { name: 'Gestor da Empresa A' })).toBeVisible()
  await expect(page.getByText('vendedor@empresa-a.dev')).toHaveCount(0)

  await page.getByRole('button', { name: 'Gestor da Empresa A' }).click()
  const detalhe = page.getByRole('dialog', { name: 'Detalhe do usuário' })
  await expect(detalhe.getByRole('heading', { name: 'Gestor da Empresa A' })).toBeVisible()
  await detalhe.getByRole('tab', { name: 'Perfis e equipes' }).click()
  await expect(detalhe.getByText('Comercial · líder')).toBeVisible()
})

test('matriz de permissões de um perfil de sistema', async ({ page }) => {
  await entrar(page, 'administrador@empresa-a.dev')
  await menu(page).getByRole('link', { name: 'Perfis de acesso' }).click()
  await page.getByRole('button', { name: 'Gestor', exact: true }).click()

  await expect(page).toHaveURL(/\/admin\/perfis\/[0-9a-f-]{36}$/)
  await expect(page.getByRole('heading', { name: 'Gestor', level: 1 })).toBeVisible()
  const plataforma = page.getByRole('region', { name: 'Plataforma' })
  await expect(plataforma.getByRole('checkbox', { name: /^Usuário: Ver/ })).toBeChecked()
  await expect(plataforma.getByRole('checkbox', { name: /^Usuário: Administrar/ })).not.toBeChecked()
})

test('convite: cadastro, e-mail, definição da senha e primeiro acesso', async ({ page, browser }) => {
  const email = `e2e-${Date.now()}@teste.dev`
  await entrar(page, 'administrador@empresa-a.dev')
  await menu(page).getByRole('link', { name: 'Usuários' }).click()
  await page.getByRole('button', { name: 'Novo usuário' }).click()

  const formulario = page.getByRole('dialog', { name: 'Novo usuário' })
  await formulario.getByLabel('Nome').fill('Pessoa do Teste Ponta a Ponta')
  await formulario.getByLabel('E-mail').fill(email)
  await formulario.getByRole('checkbox', { name: /^Técnico/ }).check()
  await formulario.getByRole('button', { name: 'Criar e convidar' }).click()
  await expect(page.getByText(`Convite enviado para ${email}.`)).toBeVisible()

  // O link chega pelo e-mail (Mailpit em desenvolvimento), com o token no fragmento
  await expect.poll(() => linkDoConvite(email), { timeout: 10_000 }).not.toBeNull()
  const link = (await linkDoConvite(email)) as string
  const convidado = await (await browser.newContext()).newPage()   // outro navegador, sem sessão
  await convidado.goto(link.replace(/^https?:\/\/[^/]+/, ''))
  await expect(convidado.getByRole('heading', { name: 'Boas-vindas, Pessoa' })).toBeVisible()
  expect(new URL(convidado.url()).hash).toBe('')   // o token sai da barra de endereço

  const novaSenha = `Nova${Date.now()}x`
  await convidado.getByLabel('Senha nova', { exact: true }).fill(novaSenha)
  await convidado.getByLabel('Repita a senha').fill(novaSenha)
  await convidado.getByRole('button', { name: 'Definir a senha' }).click()
  await expect(convidado.getByRole('heading', { name: 'Senha definida' })).toBeVisible()

  await convidado.getByRole('button', { name: 'Ir para a entrada' }).click()
  await entrar(convidado, email, novaSenha)
  await expect(convidado.getByRole('heading', { name: 'Olá, Pessoa' })).toBeVisible()
})

test('sair volta para a entrada com o aviso', async ({ page }) => {
  await entrar(page, 'marketing@empresa-a.dev')
  await page.getByRole('button', { name: 'Menu do usuário' }).click()
  await page.getByRole('button', { name: 'Sair' }).click()
  await expect(page.getByRole('status').filter({ hasText: 'Você saiu da plataforma.' })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Entrar' })).toBeVisible()
})

test('esqueci a senha responde igual para qualquer e-mail', async ({ page }) => {
  await page.goto('/esqueci-senha')
  await page.getByLabel('E-mail').fill(`ninguem-${Date.now()}@teste.dev`)
  await page.getByRole('button', { name: 'Enviar o link' }).click()
  await expect(page.getByRole('heading', { name: 'Confira o seu e-mail' })).toBeVisible()
})

async function linkDoConvite(email: string): Promise<string | null> {
  const busca = await (await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:${email}`)}`)).json()
  const id = busca.messages?.[0]?.ID
  if (!id) return null
  const mensagem = await (await fetch(`${MAILPIT}/api/v1/message/${id}`)).json()
  return /https?:\/\/\S+\/definir-senha#token=[A-Za-z0-9_-]+/.exec(mensagem.Text)?.[0] ?? null
}
