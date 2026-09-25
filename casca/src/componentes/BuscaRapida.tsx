import { useEffect, useMemo, useRef, useState, type KeyboardEvent, type ReactNode } from 'react'
import { CornerDownLeft, House, LoaderCircle, Search, UserRound, UsersRound } from 'lucide-react'
import { cx, Modal } from '@/components/ui'
import { chamar } from '../plataforma/api'
import { itensDeAdministracao, useCasca, type EstadoDoMenu } from '../plataforma/contexto'
import { normalizar } from '../plataforma/formato'
import { caminhoDoModulo, navegar } from '../plataforma/rotas'
import type { ItemDaBusca } from '../plataforma/tipos'
import { IconeDoModulo } from './Icone'

interface Destino {
  grupo: string
  rotulo: string
  detalhe?: string
  caminho: string
  icone: ReactNode
}

const ICONE = 'size-4 shrink-0 text-texto-3'

/** Contrato §8.6 e §9.6: q com 2 letras ou mais; cada módulo tem 3 s, depois é deixado de fora. */
const MINIMO_DA_BUSCA = 2
const ESPERA_ENTRE_TECLAS = 250
const TEMPO_POR_MODULO = 3_000

/** Quem responde GET .../busca?q= e como abrir o que ele devolve. */
interface Fonte {
  grupo: string
  url: string
  caminho: (rota: string) => string
  icone: ReactNode
}

function fontesDaBusca(menu: EstadoDoMenu, tem: (permissao: string) => boolean): Fonte[] {
  const fontes: Fonte[] = []
  // As telas do identity são da casca: a rota que ele devolve já é um caminho daqui
  if (tem('identity.usuario.ver')) {
    fontes.push({ grupo: 'Usuários', url: '/api/identity/busca', caminho: (rota) => rota, icone: <UsersRound aria-hidden className={ICONE} /> })
  }
  if (menu.estado === 'pronto') {
    for (const modulo of menu.modulos.filter((m) => m.busca && m.disponivel)) {
      fontes.push({
        grupo: modulo.nome,
        url: `${modulo.prefixoApi}/busca`,
        caminho: (rota) => caminhoDoModulo(modulo.codigo, rota),
        icone: <IconeDoModulo nome={modulo.icone} className={ICONE} />,
      })
    }
  }
  return fontes
}

/** Um módulo fora do ar, sem permissão (403) ou lento simplesmente não aparece (Contrato §8.6). */
async function buscarEm(fonte: Fonte, termo: string, cancelamento: AbortSignal): Promise<Destino[]> {
  const limite = new AbortController()
  const cancelar = () => limite.abort()
  const temporizador = window.setTimeout(cancelar, TEMPO_POR_MODULO)
  cancelamento.addEventListener('abort', cancelar)
  try {
    const itens = await chamar<ItemDaBusca[]>(`${fonte.url}?q=${encodeURIComponent(termo)}`, { signal: limite.signal })
    return itens.slice(0, 5).map((item) => ({
      grupo: fonte.grupo,
      rotulo: item.titulo,
      detalhe: item.subtitulo ?? undefined,
      caminho: fonte.caminho(item.rota),
      icone: fonte.icone,
    }))
  } catch {
    return []
  } finally {
    window.clearTimeout(temporizador)
    cancelamento.removeEventListener('abort', cancelar)
  }
}

/**
 * Busca rápida (Tela 11, Requisito RF55): telas da casca — módulos, submenus, administração — e,
 * a partir de duas letras, os registros dos módulos com "busca": true no registro e os usuários
 * (Contrato §8.6), consultados em paralelo e agrupados por módulo abaixo das telas.
 */
export function BuscaRapida({ aberta, aoFechar }: { aberta: boolean; aoFechar: () => void }) {
  const { menu, tem } = useCasca()
  const [termo, setTermo] = useState('')
  const [selecionado, setSelecionado] = useState(0)
  const [remotos, setRemotos] = useState<Destino[]>([])
  const [buscando, setBuscando] = useState(false)
  const lista = useRef<HTMLUListElement>(null)
  const fontes = useMemo(() => fontesDaBusca(menu, tem), [menu, tem])

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

  useEffect(() => {
    const busca = termo.trim()
    setRemotos([])
    if (!aberta || busca.length < MINIMO_DA_BUSCA || fontes.length === 0) {
      setBuscando(false)
      return
    }
    setBuscando(true)
    const cancelamento = new AbortController()
    const temporizador = window.setTimeout(() => {
      void Promise.all(fontes.map((fonte) => buscarEm(fonte, busca, cancelamento.signal))).then((respostas) => {
        if (cancelamento.signal.aborted) return
        setRemotos(respostas.flat())
        setBuscando(false)
      })
    }, ESPERA_ENTRE_TECLAS)
    // Tecla nova antes da resposta: a busca anterior é cancelada, e a lista não pisca com resultado velho
    return () => {
      window.clearTimeout(temporizador)
      cancelamento.abort()
    }
  }, [termo, aberta, fontes])

  const encontrados = useMemo(() => {
    const busca = normalizar(termo.trim())
    if (!busca) return destinos
    const telas = destinos.filter((d) => normalizar(`${d.rotulo} ${d.detalhe ?? ''} ${d.grupo}`).includes(busca))
    return [...telas, ...remotos]
  }, [destinos, remotos, termo])

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
    <Modal open={aberta} onClose={aoFechar} noPad size="lg" title="Buscar">
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
          placeholder="Buscar telas e registros…"
          aria-label="Buscar telas e registros"
          role="combobox"
          aria-expanded="true"
          aria-controls="busca-resultados"
          aria-activedescendant={encontrados[selecionado] ? `busca-${selecionado}` : undefined}
          className="h-12 min-w-0 flex-1 bg-transparent text-sm text-texto outline-none placeholder:text-texto-3"
        />
        {buscando && <LoaderCircle role="status" aria-label="Buscando nos módulos" className="size-4 shrink-0 animate-spin text-texto-3" />}
        <kbd className="rounded bg-superficie-2 px-1.5 py-0.5 text-xs text-texto-3">Esc</kbd>
      </div>

      <ul id="busca-resultados" ref={lista} role="listbox" aria-label="Resultados" className="max-h-96 overflow-y-auto py-2">
        {encontrados.length === 0 && (
          <li className="px-4 py-8 text-center text-sm text-texto-3">
            {buscando ? 'Buscando…' : `Nada encontrado para “${termo.trim()}”.`}
          </li>
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
        {fontes.length === 0
          ? 'Nenhum módulo com busca de registros está disponível para você.'
          : `A partir de ${MINIMO_DA_BUSCA} letras, busca também em: ${fontes.map((f) => f.grupo).join(', ')}.`}
      </p>
    </Modal>
  )
}
