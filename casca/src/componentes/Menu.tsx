import type { MouseEvent } from 'react'
import { House, RotateCw } from 'lucide-react'
import { caminhoDoModulo, navegar, type Rota } from '../plataforma/rotas'
import type { ModuloDoMenu } from '../plataforma/tipos'
import { IconeDoModulo } from './Icone'

export type EstadoDoMenu =
  | { estado: 'carregando' }
  | { estado: 'erro' }
  | { estado: 'pronto'; modulos: ModuloDoMenu[] }

interface Props {
  menu: EstadoDoMenu
  rota: Rota
  aoTentarDeNovo: () => void
  aoNavegar?: () => void
}

/** Clique comum navega sem recarregar; com Ctrl, Shift ou botão do meio, o navegador decide. */
function seguir(evento: MouseEvent<HTMLAnchorElement>, aoNavegar?: () => void) {
  if (evento.button !== 0 || evento.metaKey || evento.ctrlKey || evento.shiftKey || evento.altKey) return
  evento.preventDefault()
  navegar(evento.currentTarget.getAttribute('href') ?? '/')
  aoNavegar?.()
}

const ITEM = 'flex items-center gap-2.5 rounded-md px-2.5 py-2 text-sm transition-colors'
const ITEM_INATIVO = 'text-sidebar-foreground/80 hover:bg-sidebar-accent hover:text-sidebar-accent-foreground'
const ITEM_ATIVO = 'bg-sidebar-accent font-medium text-sidebar-accent-foreground'

function subrotaAtiva(rota: Rota, codigo: string, alvo: string) {
  if (rota.tipo !== 'modulo' || rota.codigo !== codigo) return false
  const caminho = rota.subrota.split('?')[0]
  return alvo === '/' ? caminho === '/' : caminho === alvo || caminho.startsWith(`${alvo}/`)
}

/** Menu montado do registro de módulos, já filtrado pelas permissões do usuário (RF33, RF34). */
export function Menu({ menu, rota, aoTentarDeNovo, aoNavegar }: Props) {
  return (
    <nav aria-label="Principal" className="flex-1 overflow-y-auto px-3 py-4">
      <a
        href="/"
        onClick={(e) => seguir(e, aoNavegar)}
        aria-current={rota.tipo === 'inicio' ? 'page' : undefined}
        className={`${ITEM} ${rota.tipo === 'inicio' ? ITEM_ATIVO : ITEM_INATIVO}`}
      >
        <House aria-hidden className="size-4 shrink-0" />
        Início
      </a>

      <p className="mt-6 mb-1 px-2.5 text-xs font-medium text-muted-foreground">Módulos</p>

      {menu.estado === 'carregando' && (
        <div className="grid gap-2 px-2.5 py-1" aria-hidden>
          {[0, 1, 2].map((i) => <div key={i} className="h-5 animate-pulse rounded bg-sidebar-accent" />)}
        </div>
      )}

      {menu.estado === 'erro' && (
        <div className="px-2.5 text-sm text-muted-foreground">
          <p>Não foi possível carregar o menu.</p>
          <button type="button" onClick={aoTentarDeNovo} className="mt-2 inline-flex items-center gap-1.5 font-medium text-foreground hover:underline">
            <RotateCw aria-hidden className="size-3.5" /> Tentar de novo
          </button>
        </div>
      )}

      {menu.estado === 'pronto' && menu.modulos.length === 0 && (
        <p className="px-2.5 text-sm text-muted-foreground">Nenhum módulo liberado para o seu perfil.</p>
      )}

      {menu.estado === 'pronto' && menu.modulos.length > 0 && (
        <ul className="grid gap-0.5">
          {menu.modulos.map((modulo) => {
            const ativo = rota.tipo === 'modulo' && rota.codigo === modulo.codigo
            return (
              <li key={modulo.codigo}>
                <a
                  href={caminhoDoModulo(modulo.codigo)}
                  onClick={(e) => seguir(e, aoNavegar)}
                  aria-current={ativo && rota.tipo === 'modulo' && rota.subrota === '/' ? 'page' : undefined}
                  className={`${ITEM} ${ativo ? ITEM_ATIVO : ITEM_INATIVO}`}
                >
                  <IconeDoModulo nome={modulo.icone} className="size-4 shrink-0" />
                  <span className="min-w-0 flex-1 truncate">{modulo.nome}</span>
                  {!modulo.disponivel && (
                    <span className="flex items-center gap-1 text-xs font-normal text-muted-foreground" title="O módulo não respondeu à última verificação">
                      <span aria-hidden className="size-1.5 rounded-full bg-destructive" />
                      indisponível
                    </span>
                  )}
                </a>
                {ativo && modulo.itensSubmenu.length > 0 && (
                  <ul className="mt-0.5 mb-1 ml-[1.1rem] grid gap-0.5 border-l pl-2.5">
                    {modulo.itensSubmenu.map((item) => {
                      const itemAtivo = subrotaAtiva(rota, modulo.codigo, item.rota)
                      return (
                        <li key={item.rota}>
                          <a
                            href={caminhoDoModulo(modulo.codigo, item.rota)}
                            onClick={(e) => seguir(e, aoNavegar)}
                            aria-current={itemAtivo ? 'page' : undefined}
                            className={`block rounded-md px-2.5 py-1.5 text-sm ${itemAtivo ? 'font-medium text-sidebar-accent-foreground' : 'text-muted-foreground hover:text-sidebar-foreground'}`}
                          >
                            {item.nome}
                          </a>
                        </li>
                      )
                    })}
                  </ul>
                )}
              </li>
            )
          })}
        </ul>
      )}
    </nav>
  )
}
