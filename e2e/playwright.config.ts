import { defineConfig, devices } from '@playwright/test'

/**
 * Roda contra o compose do infra no ar (perfis plataforma e exemplo). O endereço precisa ser
 * localhost: o cookie de refresh é Secure, e o navegador só o aceita em http://localhost.
 * CANAL_DO_NAVEGADOR=chrome (ou msedge) usa o navegador já instalado, sem `playwright install`.
 */
export default defineConfig({
  testDir: './testes',
  // O segundo fator às vezes espera o próximo passo de 30 s para ter um código novo
  timeout: 90_000,
  workers: 1,
  reporter: [['list']],
  use: {
    baseURL: process.env.PLATAFORMA_URL ?? 'http://localhost:8080',
    locale: 'pt-BR',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'], channel: process.env.CANAL_DO_NAVEGADOR || undefined } }],
})
