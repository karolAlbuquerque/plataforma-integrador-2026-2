import { useEffect, useMemo, useRef, useState, type KeyboardEvent, type ReactNode } from 'react'
import { CornerDownLeft, House, Search, UserRound } from 'lucide-react'
import { cx, Modal } from '@/components/ui'
import { itensDeAdministracao, useCasca } from '../plataforma/contexto'
import { normalizar } from '../plataforma/formato'
import { caminhoDoModulo, navegar } from '../plataforma/rotas'
import { IconeDoModulo } from './Icone'

interface Destino {
  grupo: string
  rotulo: string
  detalhe?: string
  caminho: string
  icone: ReactNode
}

const ICONE = 'size-4 shrink-0 text-texto-3'

/**
 * Busca rápida (Tela 11): por enquanto encontra telas — módulos, itens de submenu, administração.
 * A busca de registros dos módulos (empresas, contatos...) chega com a busca global (Contrato §8.6).
 */
export function BuscaRapida({ aberta, aoFechar }: { aberta: boolean; aoFechar: () => void }) {
  const { menu, tem } = useCasca()
  const [termo, setTermo] = useState('')
  const [selecionado, setSelecionado] = useState(0)
  const lista = useRef<HTMLUListElement>(null)

  useEffect(() => {
    if (aberta) {
      setTermo('')
      setSelecionado(0)
    }
  }, [aberta])

  const destinos = useMemo<Destino[]>(() => {
    const todos: Destino[] = [
      { grupo: 'Geral', rotulo: 'Início', caminho: '/', icone: <House aria-hidden className={ICONE} /> },
      { grupo: 'Geral', rotulo: 'Minha conta', detalhe: 'dados, senha e sessões', caminho: '/conta', icone: <UserRound aria-hidden className={ICONE} /> },
    ]
    if (menu.estado === 'pronto') {
      for (const modulo of menu.modulos) {
        const icone = <IconeDoModulo nome={modulo.icone} className={ICONE} />
        todos.push({ grupo: 'Módulos', rotulo: modulo.nome, caminho: caminhoDoModulo(modulo.codigo), icone })
        for (const item of modulo.itensSubmenu.filter((i) => i.rota !== '/')) {
          todos.push({ grupo: 'Módulos', rotulo: item.nome, detalhe: modulo.nome, caminho: caminhoDoModulo(modulo.codigo, item.rota), icone })
        }
      }
    }
    for (const item of itensDeAdministracao(tem)) {
      todos.push({ grupo: 'Administração', rotulo: item.nome, caminho: item.caminho, icone: <item.icone aria-hidden className={ICONE} /> })
    }
    return todos
  }, [menu, tem])

  const encontrados = useMemo(() => {
    const busca = normalizar(termo.trim())
    if (!busca) return destinos
    return destinos.filter((d) => normalizar(`${d.rotulo} ${d.detalhe ?? ''} ${d.grupo}`).includes(busca))
  }, [destinos, termo])

  function abrir(destino: Destino | undefined) {
    if (!destino) return
    aoFechar()
    navegar(destino.caminho)
  }

  function teclas(evento: KeyboardEvent<HTMLInputElement>) {
    if (evento.key === 'ArrowDown' || evento.key === 'ArrowUp') {
      evento.preventDefault()
      const passo = evento.key === 'ArrowDown' ? 1 : -1
      const proximo = (selecionado + passo + encontrados.length) % Math.max(encontrados.length, 1)
      setSelecionado(proximo)
      lista.current?.querySelectorAll('[role="option"]')[proximo]?.scrollIntoView({ block: 'nearest' })
    } else if (evento.key === 'Enter') {
      evento.preventDefault()
      abrir(encontrados[selecionado])
    }
  }

  let grupoAnterior = ''
  return (
    <Modal open={aberta} onClose={aoFechar} noPad size="lg" title="Buscar telas">
      <div className="flex items-center gap-3 border-b border-borda px-4">
        <Search aria-hidden className="size-4 shrink-0 text-texto-3" />
        <input
          data-autofocus
          value={termo}
          onChange={(e) => {
            setTermo(e.target.value)
            setSelecionado(0)
          }}
          onKeyDown={teclas}
          placeholder="Buscar módulos e telas…"
          aria-label="Buscar módulos e telas"
          role="combobox"
          aria-expanded="true"
          aria-controls="busca-resultados"
          aria-activedescendant={encontrados[selecionado] ? `busca-${selecionado}` : undefined}
          className="h-12 min-w-0 flex-1 bg-transparent text-sm text-texto outline-none placeholder:text-texto-3"
        />
        <kbd className="rounded bg-superficie-2 px-1.5 py-0.5 text-xs text-texto-3">Esc</kbd>
      </div>

      <ul id="busca-resultados" ref={lista} role="listbox" aria-label="Resultados" className="max-h-96 overflow-y-auto py-2">
        {encontrados.length === 0 && (
          <li className="px-4 py-8 text-center text-sm text-texto-3">Nada encontrado para “{termo.trim()}”.</li>
        )}
        {encontrados.map((destino, indice) => {
          const cabecalho = destino.grupo !== grupoAnterior ? destino.grupo : null
          grupoAnterior = destino.grupo
          return (
            <li key={`${destino.caminho}-${indice}`}>
              {cabecalho && (
                <p aria-hidden className="px-4 pt-2 pb-1 text-xs font-semibold tracking-wider text-texto-3 uppercase">{cabecalho}</p>
              )}
              <div
                id={`busca-${indice}`}
                role="option"
                aria-selected={indice === selecionado}
                onMouseMove={() => setSelecionado(indice)}
                onClick={() => abrir(destino)}
                className={cx(
                  'mx-2 flex cursor-pointer items-center gap-3 rounded-lg px-3 py-2 text-sm',
                  indice === selecionado ? 'bg-brand-50 text-titulo ring-1 ring-brand-700 dark:bg-brand-800/25' : 'text-texto',
                )}
              >
                {destino.icone}
                <span className="min-w-0 flex-1 truncate">
                  {destino.rotulo}
                  {destino.detalhe && <span className="text-texto-3"> — {destino.detalhe}</span>}
                </span>
                {indice === selecionado && <CornerDownLeft aria-hidden className="size-3.5 shrink-0 text-texto-3" />}
              </div>
            </li>
          )
        })}
      </ul>

      <p className="border-t border-borda bg-superficie-2 px-4 py-2 text-xs text-texto-3">
        A busca de registros dos módulos — empresas, contatos, contratos — chega com a busca global.
      </p>
    </Modal>
  )
}
