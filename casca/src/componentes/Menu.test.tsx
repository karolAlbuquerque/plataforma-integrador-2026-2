import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { moduloExemplo, renderNaCasca } from '../test/casca'
import { BarraLateral } from './Menu'

describe('BarraLateral', () => {
  it('mostra o módulo e a administração de quem tem permissão', () => {
    renderNaCasca(<BarraLateral recolhida={false} aoAlternar={() => {}} />)

    expect(screen.getByRole('link', { name: 'Exemplo' })).toHaveAttribute('href', '/app/exemplo/')
    expect(screen.getByRole('link', { name: 'Usuários' })).toBeVisible()
    expect(screen.queryByRole('link', { name: 'Perfis de acesso' })).not.toBeInTheDocument()
  })

  it('sem identity.acessar não mostra a administração', () => {
    renderNaCasca(<BarraLateral recolhida={false} />, { permissoes: ['identity.usuario.ver'] })

    expect(screen.getByRole('link', { name: 'Início' })).toBeVisible()
    expect(screen.queryByRole('link', { name: 'Usuários' })).not.toBeInTheDocument()
  })

  it('recolhida deixa o rótulo só para o leitor de tela', () => {
    renderNaCasca(<BarraLateral recolhida aoAlternar={() => {}} />)

    expect(screen.getByRole('link', { name: 'Exemplo' }).querySelector('span')).toHaveClass('sr-only')
    expect(screen.getByRole('button', { name: 'Expandir o menu' })).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Recolher o menu' })).not.toBeInTheDocument()
  })

  it('a gaveta não recolhe e avisa a casca ao navegar', async () => {
    const aoNavegar = vi.fn()
    renderNaCasca(<BarraLateral recolhida={false} aoNavegar={aoNavegar} />)

    expect(screen.queryByRole('button', { name: 'Recolher o menu' })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('link', { name: 'Exemplo' }))
    expect(aoNavegar).toHaveBeenCalledOnce()
  })

  it('com o menu em erro, oferece tentar de novo', async () => {
    const recarregarMenu = vi.fn()
    renderNaCasca(<BarraLateral recolhida={false} />, { menu: { estado: 'erro' }, recarregarMenu })

    await userEvent.click(screen.getByRole('button', { name: 'Tentar de novo' }))
    expect(recarregarMenu).toHaveBeenCalledOnce()
  })

  it('marca o módulo que não respondeu', () => {
    renderNaCasca(<BarraLateral recolhida={false} />, {
      menu: { estado: 'pronto', modulos: [{ ...moduloExemplo, disponivel: false }] },
    })

    expect(screen.getByRole('link', { name: /Exemplo\s*indisponível/ })).toBeVisible()
  })
})
