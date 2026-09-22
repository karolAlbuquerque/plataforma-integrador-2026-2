import { useEffect } from 'react'
import { Bell, LogOut, Menu as IconeMenu, Moon, Search, Sun, UserRound } from 'lucide-react'
import { iniciais } from '@/components/ui'
import { ADMINISTRACAO, useCasca, type EstadoDoMenu } from '../plataforma/contexto'
import { useRota, type Rota } from '../plataforma/rotas'
import { sair } from '../plataforma/sessao'
import { Link, Popover } from './Link'

const MAC = /Mac|iPhone|iPad/.test(navigator.userAgent)

/** "Administração / Usuários", "Módulos / CRM / Funil" — o contexto da tela, no topo. */
export function migalhas(rota: Rota, menu: EstadoDoMenu): string[] {
  const adm = (caminho: string) => ['Administração', ADMINISTRACAO.find((item) => item.caminho === caminho)?.nome ?? '']
  switch (rota.tipo) {
    case 'inicio': return ['Início']
    case 'conta': return ['Minha conta']
    case 'usuarios': return adm('/admin/usuarios')
    case 'perfis': return adm('/admin/perfis')
    case 'perfil': return [...adm('/admin/perfis'), 'Permissões']
    case 'equipes': return adm('/admin/equipes')
    case 'modulo': {
      const modulo = menu.estado === 'pronto' ? menu.modulos.find((m) => m.codigo === rota.codigo) : undefined
      if (!modulo) return ['Módulos']
      const caminho = rota.subrota.split('?')[0]
      const item = modulo.itensSubmenu.find((i) => i.rota !== '/' && (caminho === i.rota || caminho.startsWith(`${i.rota}/`)))
      return ['Módulos', modulo.nome, ...(item ? [item.nome] : [])]
    }
    default: return ['Página não encontrada']
  }
}

const ICONE_DO_CABECALHO = 'grid size-8 place-items-center rounded-lg text-texto-2 transition-colors hover:bg-superficie-2 hover:text-texto'
const ITEM_DO_MENU = 'flex w-full items-center gap-2.5 px-4 py-2 text-left text-sm text-texto hover:bg-superficie-2'

/** Header global (Tela 1): migalhas, busca, notificações e o avatar com o menu do usuário. */
export function Cabecalho({ aoAbrirMenu, aoAbrirBusca }: { aoAbrirMenu: () => void; aoAbrirBusca: () => void }) {
  const { sessao, eu, tema, alternarTema, menu } = useCasca()
  const rota = useRota()
  const partes = migalhas(rota, menu)

  useEffect(() => {
    document.title = `${partes[partes.length - 1]} · Centinela`
  }, [partes.join('/')])   // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <header className="sticky top-0 z-30 flex h-12 shrink-0 items-center gap-2 border-b border-borda bg-superficie px-3 sm:px-4">
      <button type="button" onClick={aoAbrirMenu} aria-label="Abrir o menu" className={`${ICONE_DO_CABECALHO} -ml-1 md:hidden`}>
        <IconeMenu aria-hidden className="size-5" />
      </button>

      <nav aria-label="Você está em" className="min-w-0 flex-1">
        <ol className="flex min-w-0 items-center gap-1.5 text-xs text-texto-3">
          {partes.map((parte, i) => (
            <li key={`${i}-${parte}`} className={i === partes.length - 1 ? 'truncate font-medium text-texto-2' : 'hidden shrink-0 sm:block'}>
              {i > 0 && <span aria-hidden className="mr-1.5 hidden sm:inline">/</span>}
              {parte}
            </li>
          ))}
        </ol>
      </nav>

      <button
        type="button"
        onClick={aoAbrirBusca}
        aria-keyshortcuts={MAC ? 'Meta+K' : 'Control+K'}
        className="flex h-8 items-center gap-2 rounded-lg border border-borda px-2.5 text-xs text-texto-3 transition-colors hover:bg-superficie-2 hover:text-texto"
      >
        <Search aria-hidden className="size-4" />
        <span className="hidden sm:inline">Buscar</span>
        <kbd className="hidden rounded bg-superficie-2 px-1.5 py-0.5 font-sans text-xs text-texto-3 sm:inline">{MAC ? '⌘K' : 'Ctrl K'}</kbd>
      </button>

      <Popover rotulo="Notificações" classeDoGatilho={ICONE_DO_CABECALHO} gatilho={<Bell aria-hidden className="size-4.5" />}>
        {() => (
          <>
            <p className="border-b border-borda px-4 py-3 text-sm font-semibold text-titulo">Notificações</p>
            <div className="flex flex-col items-center px-6 py-8 text-center">
              <Bell aria-hidden className="size-6 text-texto-3" />
              <p className="mt-2 text-sm text-texto-2">Nenhuma notificação por enquanto.</p>
              <p className="mt-1 text-xs text-texto-3">Os avisos dos módulos aparecem aqui.</p>
            </div>
          </>
        )}
      </Popover>

      <Popover
        rotulo="Menu do usuário"
        classeDoGatilho="grid size-8 place-items-center rounded-full bg-brand-700 text-xs font-semibold text-white transition-colors hover:bg-brand-800"
        gatilho={iniciais(sessao.usuario.nome)}
        largura="w-64"
      >
        {(fechar) => (
          <>
            <div className="border-b border-borda px-4 py-3">
              <p className="truncate text-sm font-semibold text-titulo">{sessao.usuario.nome}</p>
              <p className="truncate text-xs text-texto-3">{sessao.usuario.email}</p>
              {eu?.tenant.nome && <p className="mt-1 truncate text-xs font-medium text-brand-700 dark:text-brand-400">{eu.tenant.nome}</p>}
            </div>
            <div className="py-1">
              <Link href="/conta" onNavigate={fechar} className={ITEM_DO_MENU}>
                <UserRound aria-hidden className="size-4 shrink-0 text-texto-3" /> Minha conta
              </Link>
              <button type="button" onClick={alternarTema} className={ITEM_DO_MENU}>
                {tema === 'claro' ? <Moon aria-hidden className="size-4 shrink-0 text-texto-3" /> : <Sun aria-hidden className="size-4 shrink-0 text-texto-3" />}
                {tema === 'claro' ? 'Tema escuro' : 'Tema claro'}
              </button>
            </div>
            <div className="border-t border-borda py-1">
              <button type="button" onClick={() => void sair()} className={ITEM_DO_MENU}>
                <LogOut aria-hidden className="size-4 shrink-0 text-texto-3" /> Sair
              </button>
            </div>
          </>
        )}
      </Popover>
    </header>
  )
}
