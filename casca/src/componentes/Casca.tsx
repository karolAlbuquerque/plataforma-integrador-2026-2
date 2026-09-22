import { useCallback, useEffect, useMemo, useState } from 'react'
import { X } from 'lucide-react'
import { cx } from '@/components/ui'
import { chamar } from '../plataforma/api'
import { ProvedorDaCasca, type EstadoDoMenu } from '../plataforma/contexto'
import { useRota, type Rota } from '../plataforma/rotas'
import type { Sessao } from '../plataforma/sessao'
import { aplicarTema } from '../plataforma/tema'
import type { Eu, ModuloDoMenu, Tema } from '../plataforma/tipos'
import { Auditoria } from '../telas/admin/Auditoria'
import { Equipes } from '../telas/admin/Equipes'
import { Matriz } from '../telas/admin/Matriz'
import { Perfis } from '../telas/admin/Perfis'
import { Usuarios } from '../telas/admin/Usuarios'
import { Conta } from '../telas/Conta'
import { PaginaNaoEncontrada, Protegida } from '../telas/Estados'
import { Inicio } from '../telas/Inicio'
import { AreaDoModulo } from './AreaDoModulo'
import { Avisos } from './Avisos'
import { BuscaRapida } from './BuscaRapida'
import { Cabecalho } from './Cabecalho'
import { AvisoDeFimDaSessao } from './FimDaSessao'
import { BarraLateral } from './Menu'

/** O menu é relido de tempos em tempos para refletir a disponibilidade dos módulos. */
const ATUALIZACAO_DO_MENU = 60_000
const CHAVE_DO_MENU_RECOLHIDO = 'plataforma:menu-recolhido'

function lerRecolhido() {
  try {
    return window.localStorage.getItem(CHAVE_DO_MENU_RECOLHIDO) === 'sim'
  } catch {
    return false
  }
}

/** Barra lateral, cabeçalho e área de conteúdo. Só aparece com sessão aberta. */
export function Casca({ sessao }: { sessao: Sessao }) {
  const rota = useRota()
  const [tema, setTema] = useState<Tema>(() => (document.documentElement.dataset.tema === 'escuro' ? 'escuro' : 'claro'))
  const [menu, setMenu] = useState<EstadoDoMenu>({ estado: 'carregando' })
  const [eu, setEu] = useState<Eu | null>(null)
  const [gavetaAberta, setGavetaAberta] = useState(false)
  const [buscaAberta, setBuscaAberta] = useState(false)
  const [recolhida, setRecolhida] = useState(lerRecolhido)

  const carregarMenu = useCallback(() => {
    chamar<ModuloDoMenu[]>('/api/identity/modulos')
      .then((modulos) => setMenu({ estado: 'pronto', modulos }))
      .catch(() => setMenu((atual) => (atual.estado === 'pronto' ? atual : { estado: 'erro' })))
  }, [])

  // A cada renovação o token pode trazer outras permissões (RF15): relê quem sou e o menu
  useEffect(() => {
    chamar<Eu>('/api/identity/auth/me').then(setEu).catch(() => undefined)
    carregarMenu()
  }, [sessao.token, carregarMenu])

  useEffect(() => {
    const intervalo = window.setInterval(carregarMenu, ATUALIZACAO_DO_MENU)
    return () => window.clearInterval(intervalo)
  }, [carregarMenu])

  useEffect(() => {
    function atalho(evento: KeyboardEvent) {
      if ((evento.ctrlKey || evento.metaKey) && evento.key.toLowerCase() === 'k') {
        evento.preventDefault()
        setBuscaAberta(true)
      }
    }
    window.addEventListener('keydown', atalho)
    return () => window.removeEventListener('keydown', atalho)
  }, [])

  useEffect(() => {
    if (!gavetaAberta) return
    const fechar = (evento: KeyboardEvent) => evento.key === 'Escape' && setGavetaAberta(false)
    window.addEventListener('keydown', fechar)
    return () => window.removeEventListener('keydown', fechar)
  }, [gavetaAberta])

  const alternarTema = useCallback(() => {
    setTema((atual) => {
      const novo: Tema = atual === 'claro' ? 'escuro' : 'claro'
      aplicarTema(novo)
      return novo
    })
  }, [])

  function alternarRecolhida() {
    setRecolhida((atual) => {
      try {
        window.localStorage.setItem(CHAVE_DO_MENU_RECOLHIDO, atual ? 'nao' : 'sim')
      } catch {
        // sem armazenamento, vale só até recarregar
      }
      return !atual
    })
  }

  const tem = useCallback((permissao: string) => eu?.permissoes.includes(permissao) ?? false, [eu])
  const contexto = useMemo(
    () => ({ sessao, eu, menu, tema, alternarTema, recarregarMenu: carregarMenu, tem }),
    [sessao, eu, menu, tema, alternarTema, carregarMenu, tem],
  )

  return (
    <ProvedorDaCasca valor={contexto}>
      <div className="flex min-h-dvh">
        <aside className={cx('sticky top-0 hidden h-dvh shrink-0 transition-[width] duration-200 md:block', recolhida ? 'w-16' : 'w-64')}>
          <BarraLateral recolhida={recolhida} aoAlternar={alternarRecolhida} />
        </aside>

        {gavetaAberta && (
          <div className="fixed inset-0 z-40 md:hidden" role="dialog" aria-modal="true" aria-label="Menu">
            <div className="absolute inset-0 bg-brand-950/60" onClick={() => setGavetaAberta(false)} />
            <div className="relative h-full w-72 max-w-[85vw] shadow-2xl">
              <BarraLateral recolhida={false} aoNavegar={() => setGavetaAberta(false)} />
              <button
                type="button"
                onClick={() => setGavetaAberta(false)}
                aria-label="Fechar o menu"
                className="absolute top-2 right-2 rounded-lg p-1.5 text-brand-300 hover:bg-brand-800 hover:text-white"
              >
                <X aria-hidden className="size-5" />
              </button>
            </div>
          </div>
        )}

        <div className="flex min-w-0 flex-1 flex-col">
          <Cabecalho aoAbrirMenu={() => setGavetaAberta(true)} aoAbrirBusca={() => setBuscaAberta(true)} />
          <main className="flex min-w-0 flex-1 flex-col">
            <Tela rota={rota} menu={menu} sessao={sessao} tema={tema} />
          </main>
        </div>

        <BuscaRapida aberta={buscaAberta} aoFechar={() => setBuscaAberta(false)} />
        <Avisos />
        <AvisoDeFimDaSessao />
      </div>
    </ProvedorDaCasca>
  )
}

function Tela({ rota, menu, sessao, tema }: { rota: Rota; menu: EstadoDoMenu; sessao: Sessao; tema: Tema }) {
  switch (rota.tipo) {
    case 'inicio':
      return <Inicio />
    case 'modulo':
      return (
        <AreaDoModulo
          modulo={menu.estado === 'pronto' ? menu.modulos.find((m) => m.codigo === rota.codigo) : undefined}
          carregandoMenu={menu.estado === 'carregando'}
          subrota={rota.subrota}
          versao={rota.versao}
          sessao={sessao}
          tema={tema}
        />
      )
    case 'conta':
      return <Conta />
    case 'usuarios':
      return <Protegida permissao="identity.usuario.ver"><Usuarios /></Protegida>
    case 'perfis':
      return <Protegida permissao="identity.perfil.ver"><Perfis /></Protegida>
    case 'perfil':
      return <Protegida permissao="identity.perfil.ver"><Matriz key={rota.id} id={rota.id} /></Protegida>
    case 'equipes':
      return <Protegida permissao="identity.equipe.ver_resumo"><Equipes /></Protegida>
    case 'auditoria':
      return <Protegida permissao="identity.auditoria.ver"><Auditoria /></Protegida>
    default:
      return <PaginaNaoEncontrada />
  }
}
