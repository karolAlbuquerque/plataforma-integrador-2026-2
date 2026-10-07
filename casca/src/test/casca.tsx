import type { ReactElement, ReactNode } from 'react'
import { render } from '@testing-library/react'
import { ProvedorDaCasca, type ContextoDaCasca, type EstadoDoMenu } from '../plataforma/contexto'
import type { Sessao } from '../plataforma/sessao'
import type { Eu, ModuloDoMenu, Tema } from '../plataforma/tipos'

export const moduloExemplo: ModuloDoMenu = {
  codigo: 'exemplo',
  nome: 'Exemplo',
  icone: null,
  urlFrontend: '/modulos/exemplo/',
  prefixoApi: '/api/exemplo',
  ordemMenu: 10,
  disponivel: true,
  itensSubmenu: [{ rota: '/itens', nome: 'Itens' }],
  busca: true,
}

const PERMISSOES = ['identity.acessar', 'identity.usuario.ver']

export function sessaoDeTeste(): Sessao {
  return {
    token: 'token-de-teste',
    expiraEm: Date.now() + 60_000,
    fimDaSessao: Date.now() + 8 * 60 * 60_000,
    usuario: {
      id: 'u1',
      nome: 'Administrador da Empresa A',
      email: 'administrador@empresa-a.dev',
      tenantId: 'a0000000-0000-4000-8000-00000000000a',
      tema: 'claro',
    },
  }
}

export function euDeTeste(permissoes = PERMISSOES): Eu {
  return {
    usuario: sessaoDeTeste().usuario,
    tenant: { id: 'a0000000-0000-4000-8000-00000000000a', nome: 'Empresa A' },
    perfis: ['ADMINISTRADOR'],
    permissoes,
    equipes: [],
  }
}

/** Renderiza um pedaço da casca com sessão, menu e permissões, sem API. */
export function renderNaCasca(
  ui: ReactElement,
  opcoes: { menu?: EstadoDoMenu; permissoes?: string[]; recarregarMenu?: () => void; tema?: Tema } = {},
) {
  const permissoes = opcoes.permissoes ?? PERMISSOES
  const tema = opcoes.tema ?? 'claro'
  const valor: ContextoDaCasca = {
    sessao: sessaoDeTeste(),
    eu: euDeTeste(permissoes),
    menu: opcoes.menu ?? { estado: 'pronto', modulos: [moduloExemplo] },
    tema,
    preferenciaDeTema: tema,
    definirPreferenciaDeTema: () => {},
    alternarTema: () => {},
    recarregarMenu: opcoes.recarregarMenu ?? (() => {}),
    tem: (permissao) => permissoes.includes(permissao),
  }
  function Provedor({ children }: { children: ReactNode }) {
    return <ProvedorDaCasca valor={valor}>{children}</ProvedorDaCasca>
  }
  return render(ui, { wrapper: Provedor })
}
