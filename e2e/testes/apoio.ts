import { expect, type Page } from '@playwright/test'

/** Senha dos usuários de teste (docs/usuarios-de-teste.md do infra) e o Mailpit do compose. */
export const SENHA = process.env.SENHA_DE_TESTE ?? 'Plataforma2026'
export const MAILPIT = process.env.MAILPIT_URL ?? 'http://localhost:8025'
export const EMPRESA_A = 'a0000000-0000-4000-8000-00000000000a'

export async function entrar(page: Page, email: string, senha = SENHA) {
  await page.goto('/')
  await page.getByLabel('E-mail').fill(email)
  await page.getByLabel('Senha', { exact: true }).fill(senha)
  await page.getByRole('button', { name: 'Entrar', exact: true }).click()
  await expect(page.getByRole('navigation', { name: 'Principal' })).toBeVisible()
}

/** Navegação interna da casca, sem recarregar a página (como um clique num link). */
export async function irPara(page: Page, caminho: string) {
  await page.evaluate((destino) => {
    window.history.pushState(null, '', destino)
    window.dispatchEvent(new PopStateEvent('popstate'))
  }, caminho)
}

export const menu = (page: Page) => page.getByRole('navigation', { name: 'Principal' })
