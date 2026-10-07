import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { Casca } from './Casca'
import { euDeTeste, moduloExemplo, sessaoDeTeste } from '../test/casca'

function envelope(data: unknown) {
  return new Response(JSON.stringify({ success: true, data, message: null, errors: [] }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('Casca', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('no celular o menu abre na gaveta e o Esc fecha', async () => {
    vi.stubGlobal('fetch', vi.fn(async (entrada: RequestInfo) => {
      const caminho = String(entrada)
      if (caminho.includes('/auth/me')) return envelope(euDeTeste())
      if (caminho.includes('/modulos')) return envelope([moduloExemplo])
      return envelope(null)
    }))

    render(<Casca sessao={sessaoDeTeste()} />)
    await waitFor(() => expect(screen.getByRole('link', { name: 'Exemplo' })).toBeVisible())

    await userEvent.click(screen.getByRole('button', { name: 'Abrir o menu' }))
    const gaveta = screen.getByRole('dialog', { name: 'Menu' })
    expect(gaveta).toBeVisible()
    expect(gaveta).toHaveTextContent('Exemplo')

    await userEvent.keyboard('{Escape}')
    expect(screen.queryByRole('dialog', { name: 'Menu' })).not.toBeInTheDocument()
  })
})
