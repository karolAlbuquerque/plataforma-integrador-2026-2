import { expect, test, type Page } from '@playwright/test'
import { entrar, menu } from './apoio'

/**
 * CT-17 / RNF12: a casca em 360 px e em 1920 px, com menu utilizável e sem rolagem horizontal.
 * No celular o menu é gaveta; na largura de mesa ele fica na barra lateral.
 */

const ADMIN = 'administrador@empresa-a.dev'

async function semRolagemHorizontal(page: Page) {
  const excesso = await page.evaluate(() => {
    const raiz = document.documentElement
    const corpo = document.body
    return Math.max(raiz.scrollWidth - raiz.clientWidth, corpo.scrollWidth - corpo.clientWidth)
  })
  expect(excesso).toBeLessThanOrEqual(0)
}

test('CT-17: menu e conteúdo em 360 px e em 1920 px, sem rolagem horizontal', async ({ page }) => {
  await page.setViewportSize({ width: 1920, height: 1080 })
  await entrar(page, ADMIN)

  await expect(menu(page)).toBeVisible()
  await expect(page.getByRole('button', { name: 'Abrir o menu' })).toBeHidden()
  await expect(page.getByRole('heading', { name: 'Olá, Administrador' })).toBeVisible()
  await menu(page).getByRole('link', { name: 'Início' }).click()
  await semRolagemHorizontal(page)

  await page.setViewportSize({ width: 360, height: 800 })
  await expect(menu(page)).toBeHidden()
  await expect(page.getByRole('heading', { name: 'Olá, Administrador' })).toBeVisible()
  await semRolagemHorizontal(page)

  await page.getByRole('button', { name: 'Abrir o menu' }).click()
  const gaveta = page.getByRole('dialog', { name: 'Menu' })
  await expect(gaveta.getByRole('link', { name: 'Início' })).toBeVisible()
  await semRolagemHorizontal(page)

  await gaveta.getByRole('link', { name: 'Início' }).click()
  await expect(gaveta).toBeHidden()
  await expect(page.getByRole('heading', { name: 'Olá, Administrador' })).toBeVisible()
  await semRolagemHorizontal(page)
})
