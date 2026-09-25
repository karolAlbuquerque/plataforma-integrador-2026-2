import { readFile } from 'node:fs/promises'
import { randomUUID } from 'node:crypto'
import { expect, test } from '@playwright/test'
import amqp from 'amqplib'
import { EMPRESA_A, SENHA, entrar, menu } from './apoio'

/**
 * Onda 3 na casca: o sino (RF54), a auditoria (RF50) e o aviso de fim da sessão (RF41).
 * O teste do sino publica no RabbitMQ como o módulo de exemplo: defina MQ_EXEMPLO_SENHA com o valor
 * do .env do infra (a variável nunca é impressa).
 */
const MQ_EXEMPLO_SENHA = process.env.MQ_EXEMPLO_SENHA
const RABBITMQ_VHOST = process.env.RABBITMQ_VHOST ?? 'plataforma'

/** Pedido de notificação como um módulo faria: conectado como mq_exemplo e com user_id (Contrato §9.7). */
async function pedirNotificacao(usuarioId: string, titulo: string) {
  const conexao = await amqp.connect({
    hostname: 'localhost', port: 5672, username: 'mq_exemplo', password: MQ_EXEMPLO_SENHA, vhost: RABBITMQ_VHOST,
  })
  try {
    const canal = await conexao.createConfirmChannel()
    const envelope = {
      id: randomUUID(), tipo: 'identity.notificacao.criar', versao: 1, tenantId: EMPRESA_A, moduloOrigem: 'exemplo',
      ocorridoEm: new Date().toISOString(), usuarioId: null, correlacaoId: `e2e-${Date.now()}`,
      dados: { usuarioId, categoria: 'TAREFA_VENCIDA', titulo, texto: 'Pedida pelo teste ponta a ponta.', rota: '/' },
    }
    canal.publish('identity.entrada', 'identity.notificacao.criar', Buffer.from(JSON.stringify(envelope)), {
      contentType: 'application/json', persistent: true, userId: 'mq_exemplo',
    })
    await canal.waitForConfirms()
  } finally {
    await conexao.close()
  }
}

test('notificação pedida por um módulo aparece no sino e leva à tela dele', async ({ page, request }) => {
  test.skip(!MQ_EXEMPLO_SENHA, 'defina MQ_EXEMPLO_SENHA para publicar como mq_exemplo')
  const email = 'administrador@empresa-a.dev'
  const login = await request.post('/api/identity/auth/login', { data: { email, senha: SENHA } })
  const usuarioId = (await login.json()).data.usuario.id as string
  const titulo = `Tarefa T-${Date.now()} venceu`
  await pedirNotificacao(usuarioId, titulo)

  await entrar(page, email)
  const sino = page.getByRole('button', { name: /^Notificações, \d+ não lidas?$/ })
  await expect(sino).toBeVisible({ timeout: 10_000 })
  await sino.click()
  const item = page.getByRole('button').filter({ hasText: titulo })
  await expect(item).toContainText('não lida')
  await item.click()

  // A rota "/" é relativa ao front do exemplo: a casca abre o módulo
  await expect(page).toHaveURL(/\/app\/exemplo\/$/)
  await expect(page.frameLocator('iframe[title="Exemplo"]').getByRole('heading', { name: 'Itens' })).toBeVisible()
  await page.getByRole('button', { name: /^Notificações/ }).click()
  await expect(page.getByRole('button').filter({ hasText: titulo })).not.toContainText('não lida')
})

test('auditoria: consulta, filtro, detalhe e exportação em CSV', async ({ page }) => {
  await entrar(page, 'administrador@empresa-a.dev')
  await menu(page).getByRole('link', { name: 'Auditoria' }).click()
  await expect(page.getByRole('heading', { name: 'Auditoria', level: 1 })).toBeVisible()

  await page.getByLabel('Ação', { exact: true }).selectOption('login')
  const primeira = page.getByRole('row').nth(1)
  await expect(primeira).toContainText('Entrou')
  await expect(primeira).toContainText('Administrador da Empresa A')
  await primeira.click()
  const detalhe = page.getByRole('dialog', { name: 'Registro de auditoria' })
  await expect(detalhe.getByText('Sessão', { exact: true })).toBeVisible()
  await detalhe.getByRole('button', { name: 'Fechar' }).click()

  const [arquivo] = await Promise.all([page.waitForEvent('download'), page.getByRole('button', { name: 'Exportar CSV' }).click()])
  expect(arquivo.suggestedFilename()).toMatch(/^auditoria-\d{4}-\d{2}-\d{2}\.csv$/)
  const csv = await readFile(await arquivo.path(), 'utf8')
  expect(csv.startsWith('﻿ocorridoEm;usuario;usuarioId;ip;acao;entidade;')).toBe(true)
  expect(csv).toContain(';login;sessao;')
})

test('quem não tem a permissão não vê a auditoria no menu', async ({ page }) => {
  await entrar(page, 'gestor@empresa-a.dev')
  await expect(menu(page).getByRole('link', { name: 'Início' })).toBeVisible()
  await expect(menu(page).getByRole('link', { name: 'Auditoria' })).toHaveCount(0)
})

test('aviso de fim da sessão pede a senha sem tirar o módulo da tela', async ({ page }) => {
  // Relógio falso na página: as oito horas passam num instante; o servidor segue no tempo real
  await page.clock.install()
  await entrar(page, 'administrador@empresa-a.dev')
  await menu(page).getByRole('link', { name: 'Exemplo' }).click()
  const modulo = page.frameLocator('iframe[title="Exemplo"]')
  await expect(modulo.getByRole('heading', { name: 'Itens' })).toBeVisible()

  await page.clock.fastForward('07:56:00')
  const aviso = page.getByRole('dialog', { name: /^Sua sessão termina em/ })
  await expect(aviso.getByRole('heading')).toHaveText(/^Sua sessão termina em [1-5] minutos?$/)

  // De volta ao tempo real: a sessão nova vem do servidor com mais oito horas
  await page.clock.setSystemTime(new Date())
  await aviso.getByLabel(/^Senha de /).fill(SENHA)
  await aviso.getByRole('button', { name: 'Continuar' }).click()
  await expect(aviso).toHaveCount(0)
  await expect(page).toHaveURL(/\/app\/exemplo\/$/)
  await expect(modulo.getByRole('heading', { name: 'Itens' })).toBeVisible()
})
