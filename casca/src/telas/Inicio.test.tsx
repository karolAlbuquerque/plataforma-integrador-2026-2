import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { moduloExemplo, renderNaCasca } from '../test/casca'
import { Inicio } from './Inicio'

describe('Inicio', () => {
  it('mostra os módulos liberados e a administração', () => {
    renderNaCasca(<Inicio />)

    expect(screen.getByRole('heading', { name: 'Olá, Administrador' })).toBeVisible()
    expect(screen.getByRole('link', { name: /Exemplo/ })).toHaveAttribute('href', '/app/exemplo/')
    expect(screen.getByText('Itens')).toBeVisible()
    expect(screen.getByRole('link', { name: /Usuários/ })).toHaveAttribute('href', '/admin/usuarios')
  })

  it('sem permissão de acesso não mostra a administração', () => {
    renderNaCasca(<Inicio />, { permissoes: [] })

    expect(screen.queryByRole('region', { name: 'Administração' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Minha conta/ })).toBeVisible()
  })

  it('marca o módulo indisponível e o cartão encolhe na largura da tela', () => {
    renderNaCasca(<Inicio />, {
      menu: { estado: 'pronto', modulos: [{ ...moduloExemplo, nome: 'Produtos e Serviços', disponivel: false }] },
    })

    const cartao = screen.getByRole('link', { name: /Produtos e Serviços/ })
    expect(cartao).toHaveTextContent('indisponível')
    expect(cartao).toHaveClass('min-w-0', 'overflow-hidden')
  })
})
