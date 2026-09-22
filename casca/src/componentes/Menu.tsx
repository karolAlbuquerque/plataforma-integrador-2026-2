import type { ReactNode } from 'react'
import { ChevronLeft, ChevronRight, House, RotateCw } from 'lucide-react'
import { cx } from '@/components/ui'
import { itensDeAdministracao, useCasca } from '../plataforma/contexto'
import { caminhoDoModulo, useRota, type Rota } from '../plataforma/rotas'
import { IconeDoModulo } from './Icone'
import { Link } from './Link'
import { Marca } from './Marca'

/** Caminho da tela da casca que está aberta, para marcar o item ativo. */
export function caminhoDaRota(rota: Rota) {
  switch (rota.tipo) {
    case 'usuarios': return '/admin/usuarios'
    case 'perfis':
    case 'perfil': return '/admin/perfis'
    case 'equipes': return '/admin/equipes'
    case 'conta': return '/conta'
    case 'inicio': return '/'
    default: return null
  }
}

function subrotaAtiva(rota: Rota, codigo: string, alvo: string) {
  if (rota.tipo !== 'modulo' || rota.codigo !== codigo) return false
  const caminho = rota.subrota.split('?')[0]
  return alvo === '/' ? caminho === '/' : caminho === alvo || caminho.startsWith(`${alvo}/`)
}

interface Props {
  recolhida: boolean
  /** Sem ele, não há botão de recolher (a gaveta do celular). */
  aoAlternar?: () => void
  aoNavegar?: () => void
}

/**
 * Barra lateral (design system §9 e Tela 2): menu montado do registro de módulos, já filtrado
 * pelas permissões do usuário (RF33), e as telas de administração. Recolhida, mostra só os ícones.
 */
export function BarraLateral({ recolhida, aoAlternar, aoNavegar }: Props) {
  const { menu, tem, recarregarMenu } = useCasca()
  const rota = useRota()
  const administracao = itensDeAdministracao(tem)
  const ativo = caminhoDaRota(rota)

  return (
    <div className="flex h-full flex-col bg-brand-950 text-gray-300">
      <div className={cx('flex h-12 shrink-0 items-center border-b border-white/10', recolhida ? 'justify-center' : 'px-4')}>
        <Marca compacta={recolhida} />
      </div>

      <nav aria-label="Principal" className="flex-1 overflow-x-hidden overflow-y-auto py-3">
        <ul>
          <Item href="/" rotulo="Início" icone={<House aria-hidden className="size-4 shrink-0" />} ativo={ativo === '/'} recolhida={recolhida} aoNavegar={aoNavegar} />
        </ul>

        <Grupo titulo="Módulos" recolhida={recolhida}>
          {menu.estado === 'carregando' &&
            [0, 1, 2].map((i) => <li key={i} aria-hidden className="mx-3 my-2 h-5 animate-pulse rounded bg-white/10" />)}

          {menu.estado === 'erro' && !recolhida && (
            <li className="px-4 text-xs text-gray-400">
              Não foi possível carregar o menu.{' '}
              <button type="button" onClick={recarregarMenu} className="inline-flex items-center gap-1 font-medium text-brand-300 hover:text-white">
                <RotateCw aria-hidden className="size-3" /> Tentar de novo
              </button>
            </li>
          )}

          {menu.estado === 'pronto' && menu.modulos.length === 0 && !recolhida && (
            <li className="px-4 text-xs text-gray-400">Nenhum módulo liberado para o seu perfil.</li>
          )}

          {menu.estado === 'pronto' &&
            menu.modulos.map((modulo) => {
              const aberto = rota.tipo === 'modulo' && rota.codigo === modulo.codigo
              return (
                <Item
                  key={modulo.codigo}
                  href={caminhoDoModulo(modulo.codigo)}
                  rotulo={modulo.nome}
                  icone={<IconeDoModulo nome={modulo.icone} className="size-4 shrink-0" />}
                  ativo={aberto}
                  indisponivel={!modulo.disponivel}
                  recolhida={recolhida}
                  aoNavegar={aoNavegar}
                >
                  {aberto && !recolhida && modulo.itensSubmenu.length > 0 && (
                    <ul className="mt-0.5 mb-1 ml-8 border-l border-white/10 pl-2">
                      {modulo.itensSubmenu.map((item) => {
                        const itemAtivo = subrotaAtiva(rota, modulo.codigo, item.rota)
                        return (
                          <li key={item.rota}>
                            <Link
                              href={caminhoDoModulo(modulo.codigo, item.rota)}
                              onNavigate={aoNavegar}
                              aria-current={itemAtivo ? 'page' : undefined}
                              className={cx(
                                'block rounded-lg px-2.5 py-1.5 text-xs transition-colors',
                                itemAtivo ? 'font-semibold text-brand-400' : 'text-gray-400 hover:text-white',
                              )}
                            >
                              {item.nome}
                            </Link>
                          </li>
                        )
                      })}
                    </ul>
                  )}
                </Item>
              )
            })}
        </Grupo>

        {administracao.length > 0 && (
          <Grupo titulo="Administração" recolhida={recolhida}>
            {administracao.map((item) => (
              <Item
                key={item.caminho}
                href={item.caminho}
                rotulo={item.nome}
                icone={<item.icone aria-hidden className="size-4 shrink-0" />}
                ativo={ativo === item.caminho}
                recolhida={recolhida}
                aoNavegar={aoNavegar}
              />
            ))}
          </Grupo>
        )}
      </nav>

      {aoAlternar && (
        <button
          type="button"
          onClick={aoAlternar}
          aria-label={recolhida ? 'Expandir o menu' : 'Recolher o menu'}
          className={cx(
            'flex h-11 shrink-0 items-center gap-2 border-t border-white/10 text-xs text-brand-300 transition-colors hover:bg-brand-900 hover:text-white',
            recolhida ? 'justify-center' : 'px-4',
          )}
        >
          {recolhida ? <ChevronRight aria-hidden className="size-4" /> : <><ChevronLeft aria-hidden className="size-4" /> Recolher</>}
        </button>
      )}
    </div>
  )
}

function Grupo({ titulo, recolhida, children }: { titulo: string; recolhida: boolean; children: ReactNode }) {
  return (
    <div className="mt-4">
      {recolhida ? (
        <hr aria-hidden className="mx-3 mb-2 border-white/10" />
      ) : (
        <p className="mb-1 px-4 text-xs font-semibold tracking-wider text-brand-400 uppercase">{titulo}</p>
      )}
      <ul aria-label={titulo}>{children}</ul>
    </div>
  )
}

interface ItemProps {
  href: string
  rotulo: string
  icone: ReactNode
  ativo: boolean
  recolhida: boolean
  indisponivel?: boolean
  aoNavegar?: () => void
  children?: ReactNode
}

function Item({ href, rotulo, icone, ativo, recolhida, indisponivel, aoNavegar, children }: ItemProps) {
  return (
    <li>
      <Link
        href={href}
        onNavigate={aoNavegar}
        aria-current={ativo ? 'page' : undefined}
        title={recolhida ? rotulo + (indisponivel ? ' (indisponível)' : '') : undefined}
        className={cx(
          'relative mx-2 flex items-center gap-2.5 rounded-lg py-2 text-sm transition-colors',
          recolhida ? 'justify-center px-0' : 'px-2.5',
          ativo ? 'bg-brand-700 font-medium text-white' : 'text-gray-300 hover:bg-brand-800 hover:text-white',
        )}
      >
        {icone}
        <span className={recolhida ? 'sr-only' : 'min-w-0 flex-1 truncate'}>{rotulo}</span>
        {indisponivel && (
          <span
            title="O módulo não respondeu à última verificação da plataforma"
            className={cx('size-1.5 shrink-0 rounded-full bg-red-400', recolhida && 'absolute top-1.5 right-2')}
          >
            <span className="sr-only">indisponível</span>
          </span>
        )}
      </Link>
      {children}
    </li>
  )
}
