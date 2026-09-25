import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react'
import { ScrollText, ShieldCheck, Users, UsersRound, type LucideIcon } from 'lucide-react'
import { ErroDaApi, mensagemDe } from './api'
import type { Sessao } from './sessao'
import type { Eu, ModuloDoMenu, Tema } from './tipos'

export type EstadoDoMenu =
  | { estado: 'carregando' }
  | { estado: 'erro' }
  | { estado: 'pronto'; modulos: ModuloDoMenu[] }

export interface ContextoDaCasca {
  sessao: Sessao
  /** Dados do /auth/me — relidos a cada renovação, porque as permissões podem ter mudado (RF15). */
  eu: Eu | null
  menu: EstadoDoMenu
  tema: Tema
  alternarTema: () => void
  recarregarMenu: () => void
  /** A permissão está no token atual. Esconder um botão não substitui a checagem do servidor. */
  tem: (permissao: string) => boolean
}

const Contexto = createContext<ContextoDaCasca | null>(null)

export function ProvedorDaCasca({ valor, children }: { valor: ContextoDaCasca; children: ReactNode }) {
  return <Contexto.Provider value={valor}>{children}</Contexto.Provider>
}

export function useCasca(): ContextoDaCasca {
  const contexto = useContext(Contexto)
  if (!contexto) throw new Error('useCasca fora da casca')
  return contexto
}

/** Telas de administração da plataforma: exigem identity.acessar e a permissão de cada uma. */
export interface ItemDeAdministracao {
  caminho: string
  nome: string
  icone: LucideIcon
  permissao: string
}

export const ADMINISTRACAO: ItemDeAdministracao[] = [
  { caminho: '/admin/usuarios', nome: 'Usuários', icone: Users, permissao: 'identity.usuario.ver' },
  { caminho: '/admin/perfis', nome: 'Perfis de acesso', icone: ShieldCheck, permissao: 'identity.perfil.ver' },
  { caminho: '/admin/equipes', nome: 'Equipes', icone: UsersRound, permissao: 'identity.equipe.ver_resumo' },
  { caminho: '/admin/auditoria', nome: 'Auditoria', icone: ScrollText, permissao: 'identity.auditoria.ver' },
]

export function itensDeAdministracao(tem: (permissao: string) => boolean) {
  return tem('identity.acessar') ? ADMINISTRACAO.filter((item) => tem(item.permissao)) : []
}

// ------------------------------------------------------------------ carregamento

export type Carga<T> =
  | { tipo: 'carregando' }
  | { tipo: 'erro'; mensagem: string; status: number }
  | { tipo: 'pronto'; dados: T }

/**
 * Carrega dados da API e recarrega quando as dependências mudam. Enquanto recarrega, os dados
 * anteriores continuam na tela (sem piscar a lista a cada letra da busca).
 */
export function useCarregar<T>(carregar: () => Promise<T>, dependencias: unknown[]) {
  const [carga, setCarga] = useState<Carga<T>>({ tipo: 'carregando' })
  const [atualizando, setAtualizando] = useState(false)
  const [versao, setVersao] = useState(0)
  const funcao = useRef(carregar)
  funcao.current = carregar

  useEffect(() => {
    let ativo = true
    setAtualizando(true)
    funcao.current()
      .then((dados) => ativo && setCarga({ tipo: 'pronto', dados }))
      .catch((erro: unknown) => {
        if (ativo) setCarga({ tipo: 'erro', mensagem: mensagemDe(erro), status: erro instanceof ErroDaApi ? erro.status : 0 })
      })
      .finally(() => ativo && setAtualizando(false))
    return () => {
      ativo = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...dependencias, versao])

  return { carga, atualizando, recarregar: () => setVersao((v) => v + 1) }
}
