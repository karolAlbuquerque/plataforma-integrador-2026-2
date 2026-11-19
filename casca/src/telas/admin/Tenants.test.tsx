import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderNaCasca } from '../../test/casca'
import { Tenants, formatarCnpj } from './Tenants'
import { Cabecalho } from '../../componentes/Cabecalho'

function envelope(data: unknown) {
  return new Response(JSON.stringify({ success: true, data, message: null, errors: [] }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

const TENANT_A = {
  id: 'a0000000-0000-4000-8000-00000000000a',
  razaoSocial: 'Empresa A Ltda.',
  nomeFantasia: 'Empresa A',
  cnpj: '12345678000199',
  subdominio: 'empresa-a',
  ativo: true,
  totalUsuarios: 10,
  totalPerfis: 10,
  totalEquipes: 1,
  atual: true,
  criadoEm: '2026-09-20T12:00:00Z',
}

const TENANT_B = {
  id: 'b0000000-0000-4000-8000-00000000000b',
  razaoSocial: 'Empresa B S.A.',
  nomeFantasia: 'Empresa B',
  cnpj: '98765432000188',
  subdominio: 'empresa-b',
  ativo: true,
  totalUsuarios: 10,
  totalPerfis: 10,
  totalEquipes: 1,
  atual: false,
  criadoEm: '2026-09-20T12:00:00Z',
}

describe('Tenants (multi-tenant em tela)', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('formata CNPJ de 14 dígitos', () => {
    expect(formatarCnpj('12345678000199')).toBe('12.345.678/0001-99')
    expect(formatarCnpj(null)).toBeNull()
  })

  it('lista as empresas, destaca a empresa ativa e abre o formulário de edição', async () => {
    vi.stubGlobal('fetch', vi.fn(async (entrada: RequestInfo) => {
      const caminho = String(entrada)
      if (caminho.includes('/api/identity/tenants/atual')) return envelope(TENANT_A)
      if (caminho.includes('/api/identity/tenants')) {
        return envelope({ itens: [TENANT_A, TENANT_B], pagina: 0, tamanho: 24, total: 2 })
      }
      return envelope(null)
    }))

    renderNaCasca(<Tenants />, { permissoes: ['identity.acessar', 'identity.tenant.administrar'] })

    await waitFor(() => expect(screen.getByText('Empresa ativa na sessão')).toBeVisible())
    expect(screen.getAllByText('12.345.678/0001-99')).toHaveLength(2)
    expect(screen.getByText('Empresa B S.A.')).toBeVisible()
    expect(screen.getByRole('button', { name: /Entrar nesta empresa/ })).toBeVisible()

    await userEvent.click(screen.getByRole('button', { name: /Editar dados da empresa/ }))
    expect(screen.getByRole('dialog', { name: /Editar Empresa A/ })).toBeVisible()
    expect(screen.getByLabelText(/Razão social/)).toHaveValue('Empresa A Ltda.')
  })

  it('mostra o seletor de empresa no cabeçalho para quem administra tenants', async () => {
    vi.stubGlobal('fetch', vi.fn(async (entrada: RequestInfo) => {
      const caminho = String(entrada)
      if (caminho.includes('/api/identity/notificacoes/contagem')) return envelope({ naoLidas: 0 })
      if (caminho.includes('/api/identity/tenants')) {
        return envelope({ itens: [TENANT_A, TENANT_B], pagina: 0, tamanho: 50, total: 2 })
      }
      return envelope(null)
    }))

    renderNaCasca(<Cabecalho aoAbrirMenu={() => {}} aoAbrirBusca={() => {}} />, {
      permissoes: ['identity.acessar', 'identity.tenant.administrar'],
    })

    const botao = screen.getByRole('button', { name: 'Alternar empresa' })
    expect(botao).toHaveTextContent('Empresa A')
    await userEvent.click(botao)

    await waitFor(() => expect(screen.getByRole('button', { name: /Empresa B/ })).toBeVisible())
    expect(screen.getByRole('link', { name: /Gerenciar empresas/ })).toHaveAttribute('href', '/admin/tenants')
  })
})
