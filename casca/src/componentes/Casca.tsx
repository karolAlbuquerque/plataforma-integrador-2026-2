import { useCallback, useEffect, useState } from 'react'
import { LogOut, Menu as IconeMenu, Moon, Sun, X } from 'lucide-react'
import { chamar } from '../plataforma/api'
import { useRota } from '../plataforma/rotas'
import { sair, type Sessao } from '../plataforma/sessao'
import { aplicarTema } from '../plataforma/tema'
import type { Eu, ModuloDoMenu, Tema } from '../plataforma/tipos'
import { AreaDoModulo } from './AreaDoModulo'
import { Avisos } from './Avisos'
import { Inicio } from './Inicio'
import { Marca } from './Marca'
import { Menu, type EstadoDoMenu } from './Menu'

/** O menu é relido de tempos em tempos para refletir a disponibilidade dos módulos. */
const ATUALIZACAO_DO_MENU = 60_000

/** Barra, menu e área de conteúdo. Só aparece com sessão aberta. */
export function Casca({ sessao }: { sessao: Sessao }) {
  const rota = useRota()
  const [tema, setTema] = useState<Tema>(() => (document.documentElement.dataset.tema === 'escuro' ? 'escuro' : 'claro'))
  const [menu, setMenu] = useState<EstadoDoMenu>({ estado: 'carregando' })
  const [eu, setEu] = useState<Eu | null>(null)
  const [gavetaAberta, setGavetaAberta] = useState(false)

  const carregar = useCallback(async () => {
    setMenu({ estado: 'carregando' })
    try {
      const [dados, modulos] = await Promise.all([
        chamar<Eu>('/api/identity/auth/me'),
        chamar<ModuloDoMenu[]>('/api/identity/modulos'),
      ])
      setEu(dados)
      setMenu({ estado: 'pronto', modulos })
    } catch {
      setMenu({ estado: 'erro' })
    }
  }, [])

  const usuarioId = sessao.usuario.id
  useEffect(() => {
    void carregar()
    const intervalo = window.setInterval(() => {
      chamar<ModuloDoMenu[]>('/api/identity/modulos')
        .then((modulos) => setMenu({ estado: 'pronto', modulos }))
        .catch(() => undefined)   // falha momentânea: mantém o menu que já está na tela
    }, ATUALIZACAO_DO_MENU)
    return () => window.clearInterval(intervalo)
  }, [carregar, usuarioId])

  const modulos = menu.estado === 'pronto' ? menu.modulos : []
  const moduloAtual = rota.tipo === 'modulo' ? modulos.find((m) => m.codigo === rota.codigo) : undefined

  useEffect(() => {
    document.title = moduloAtual ? `${moduloAtual.nome} · Plataforma` : 'Plataforma'
  }, [moduloAtual])

  useEffect(() => {
    if (!gavetaAberta) return
    const fechar = (evento: KeyboardEvent) => evento.key === 'Escape' && setGavetaAberta(false)
    window.addEventListener('keydown', fechar)
    return () => window.removeEventListener('keydown', fechar)
  }, [gavetaAberta])

  function alternarTema() {
    const novo: Tema = tema === 'claro' ? 'escuro' : 'claro'
    aplicarTema(novo)
    setTema(novo)
  }

  const titulo = rota.tipo === 'inicio' ? 'Início' : moduloAtual?.nome ?? ''
  const menuLateral = (fecharAoNavegar?: () => void) => (
    <Menu menu={menu} rota={rota} aoTentarDeNovo={() => void carregar()} aoNavegar={fecharAoNavegar} />
  )

  return (
    <div className="flex min-h-dvh">
      <aside className="sticky top-0 hidden h-dvh w-64 shrink-0 flex-col border-r bg-sidebar text-sidebar-foreground md:flex">
        <div className="flex h-14 shrink-0 items-center border-b px-4">
          <Marca />
        </div>
        {menuLateral()}
      </aside>

      {gavetaAberta && (
        <div className="fixed inset-0 z-40 md:hidden" role="dialog" aria-modal="true" aria-label="Menu">
          <div className="absolute inset-0 bg-foreground/30" onClick={() => setGavetaAberta(false)} />
          <aside className="relative flex h-full w-72 max-w-[85vw] flex-col border-r bg-sidebar text-sidebar-foreground shadow-lg">
            <div className="flex h-14 shrink-0 items-center justify-between border-b px-4">
              <Marca />
              <button type="button" onClick={() => setGavetaAberta(false)} aria-label="Fechar menu" className="rounded-md p-1.5 hover:bg-sidebar-accent">
                <X aria-hidden className="size-5" />
              </button>
            </div>
            {menuLateral(() => setGavetaAberta(false))}
          </aside>
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-30 flex h-14 shrink-0 items-center gap-2 border-b bg-background/95 px-4 backdrop-blur">
          <button
            type="button"
            onClick={() => setGavetaAberta(true)}
            aria-label="Abrir menu"
            className="-ml-1.5 rounded-md p-1.5 hover:bg-accent md:hidden"
          >
            <IconeMenu aria-hidden className="size-5" />
          </button>
          <p className="min-w-0 flex-1 truncate text-sm font-medium">{titulo}</p>

          {eu?.tenant.nome && (
            <span className="hidden max-w-48 truncate text-sm text-muted-foreground lg:block">{eu.tenant.nome}</span>
          )}
          <span className="hidden max-w-48 truncate text-sm sm:block" title={sessao.usuario.email}>
            {sessao.usuario.nome}
          </span>
          <button
            type="button"
            onClick={alternarTema}
            aria-label={tema === 'claro' ? 'Usar tema escuro' : 'Usar tema claro'}
            title={tema === 'claro' ? 'Tema escuro' : 'Tema claro'}
            className="rounded-md p-2 text-muted-foreground hover:bg-accent hover:text-foreground"
          >
            {tema === 'claro' ? <Moon aria-hidden className="size-4" /> : <Sun aria-hidden className="size-4" />}
          </button>
          <button
            type="button"
            onClick={() => void sair()}
            className="inline-flex items-center gap-1.5 rounded-md px-2.5 py-2 text-sm text-muted-foreground hover:bg-accent hover:text-foreground"
          >
            <LogOut aria-hidden className="size-4" />
            <span className="hidden sm:inline">Sair</span>
          </button>
        </header>

        <main className="flex-1">
          {rota.tipo === 'inicio' ? (
            <Inicio eu={eu} nome={sessao.usuario.nome} menu={menu} />
          ) : (
            <AreaDoModulo
              modulo={moduloAtual}
              carregandoMenu={menu.estado === 'carregando'}
              subrota={rota.subrota}
              versao={rota.versao}
              sessao={sessao}
              tema={tema}
            />
          )}
        </main>
      </div>

      <Avisos />
    </div>
  )
}
