import { useCallback, useEffect, useState } from 'react'
import { Bell, CheckCheck, RotateCw } from 'lucide-react'
import { Button, Spinner, cx } from '@/components/ui'
import { api, mensagemDe } from '../plataforma/api'
import { avisar } from '../plataforma/avisos'
import { useCarregar, useCasca } from '../plataforma/contexto'
import { haQuanto } from '../plataforma/formato'
import { navegar } from '../plataforma/rotas'
import type { Notificacao, Pagina } from '../plataforma/tipos'
import { Popover } from './Link'

/** A contagem é leve; a lista só é pedida quando o sino abre (RF54). */
const INTERVALO_DA_CONTAGEM = 30_000
const MAIS_RECENTES = 10
const ROTA_SEGURA = /^\/(?!\/)[\w\-./~%?=&]*$/

/**
 * Onde a notificação leva: a rota é relativa ao front do módulo que a pediu (Contrato §12.2).
 * Aceita também o formato antigo, com /modulos/{codigo}/ na frente. Nulo quando não leva a lugar
 * nenhum — ou quando a rota não parece um caminho da própria plataforma.
 */
export function destinoDa(notificacao: Pick<Notificacao, 'moduloOrigem' | 'rota'>): string | null {
  const { moduloOrigem: modulo, rota } = notificacao
  if (!modulo || !rota || !/^[a-z]{2,30}$/.test(modulo)) return null
  const relativa = rota.startsWith(`/modulos/${modulo}/`) ? rota.slice(`/modulos/${modulo}`.length) : rota
  if (!ROTA_SEGURA.test(relativa) || relativa.length > 500) return null
  return `/app/${modulo}${relativa}`
}

export function SinoDeNotificacoes({ classeDoGatilho }: { classeDoGatilho: string }) {
  const { sessao } = useCasca()
  const [naoLidas, setNaoLidas] = useState(0)

  const contar = useCallback(() => {
    api.get<{ naoLidas: number }>('/api/identity/notificacoes/contagem')
      .then((contagem) => setNaoLidas(contagem.naoLidas))
      .catch(() => undefined)   // sem contagem, o sino só fica sem o número
  }, [])

  // A cada 30 s com a aba visível, e logo que ela volta a ficar visível
  useEffect(() => {
    contar()
    const intervalo = window.setInterval(() => document.visibilityState === 'visible' && contar(), INTERVALO_DA_CONTAGEM)
    const aoVoltar = () => document.visibilityState === 'visible' && contar()
    document.addEventListener('visibilitychange', aoVoltar)
    return () => {
      window.clearInterval(intervalo)
      document.removeEventListener('visibilitychange', aoVoltar)
    }
  }, [contar, sessao.usuario.id])

  const rotulo = naoLidas === 0 ? 'Notificações' : `Notificações, ${naoLidas} não ${naoLidas === 1 ? 'lida' : 'lidas'}`

  return (
    <Popover
      rotulo={rotulo}
      classeDoGatilho={cx(classeDoGatilho, 'relative')}
      largura="w-96"
      gatilho={
        <>
          <Bell aria-hidden className="size-4.5" />
          {naoLidas > 0 && (
            <span aria-hidden className="absolute -top-0.5 -right-0.5 grid h-4 min-w-4 place-items-center rounded-full bg-red-600 px-1 text-[10px] leading-none font-bold text-white">
              {naoLidas > 99 ? '99+' : naoLidas}
            </span>
          )}
        </>
      }
    >
      {(fechar) => <ListaDeNotificacoes naoLidas={naoLidas} aoMudar={contar} aoAbrir={fechar} />}
    </Popover>
  )
}

function ListaDeNotificacoes({ naoLidas, aoMudar, aoAbrir }: { naoLidas: number; aoMudar: () => void; aoAbrir: () => void }) {
  const lista = useCarregar(
    () => api.get<Pagina<Notificacao>>(`/api/identity/notificacoes?tamanho=${MAIS_RECENTES}`),
    [],
  )
  const [marcando, setMarcando] = useState(false)

  function abrir(notificacao: Notificacao) {
    if (!notificacao.lida) {
      // Não espera: a navegação não depende da marcação
      api.post(`/api/identity/notificacoes/${notificacao.id}/lida`).then(aoMudar).catch(() => undefined)
    }
    const destino = destinoDa(notificacao)
    aoAbrir()
    if (destino) navegar(destino)
  }

  async function marcarTodas() {
    setMarcando(true)
    try {
      await api.post('/api/identity/notificacoes/lidas')
      aoMudar()
      lista.recarregar()
    } catch (e) {
      avisar('erro', mensagemDe(e))
    } finally {
      setMarcando(false)
    }
  }

  return (
    <>
      <div className="flex items-center justify-between gap-2 border-b border-borda px-4 py-2.5">
        <p className="text-sm font-semibold text-titulo">Notificações</p>
        {naoLidas > 0 && (
          <Button variant="ghost" size="sm" loading={marcando} onClick={() => void marcarTodas()}>
            <CheckCheck aria-hidden className="size-3.5" /> Marcar todas como lidas
          </Button>
        )}
      </div>

      {lista.carga.tipo === 'carregando' && <div className="p-6"><Spinner /></div>}
      {lista.carga.tipo === 'erro' && (
        <div className="flex flex-col items-center gap-2 px-6 py-8 text-center">
          <p className="text-sm text-texto-2">{lista.carga.mensagem}</p>
          <Button variant="outline" size="sm" onClick={lista.recarregar}><RotateCw aria-hidden className="size-3.5" /> Tentar de novo</Button>
        </div>
      )}
      {lista.carga.tipo === 'pronto' && lista.carga.dados.itens.length === 0 && (
        <div className="flex flex-col items-center px-6 py-8 text-center">
          <Bell aria-hidden className="size-6 text-texto-3" />
          <p className="mt-2 text-sm text-texto-2">Nenhuma notificação por enquanto.</p>
          <p className="mt-1 text-xs text-texto-3">Os avisos dos módulos aparecem aqui.</p>
        </div>
      )}
      {lista.carga.tipo === 'pronto' && lista.carga.dados.itens.length > 0 && (
        <ul className="max-h-[60vh] divide-y divide-borda overflow-y-auto">
          {lista.carga.dados.itens.map((notificacao) => (
            <li key={notificacao.id}>
              <button
                type="button"
                onClick={() => abrir(notificacao)}
                className={cx('flex w-full gap-3 px-4 py-3 text-left transition-colors hover:bg-superficie-2', !notificacao.lida && 'bg-brand-50/60 dark:bg-brand-800/10')}
              >
                <span aria-hidden className={cx('mt-1.5 size-2 shrink-0 rounded-full', notificacao.lida ? 'bg-transparent' : 'bg-brand-600')} />
                <span className="min-w-0 flex-1">
                  <span className={cx('block text-sm text-titulo', !notificacao.lida && 'font-semibold')}>{notificacao.titulo}</span>
                  {notificacao.texto && <span className="mt-0.5 block text-xs text-texto-2">{notificacao.texto}</span>}
                  <span className="mt-1 block text-xs text-texto-3">
                    {haQuanto(notificacao.criadaEm)}
                    {!notificacao.lida && <span className="sr-only">, não lida</span>}
                  </span>
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
      {lista.carga.tipo === 'pronto' && lista.carga.dados.total > MAIS_RECENTES && (
        <p className="border-t border-borda px-4 py-2 text-xs text-texto-3">
          Mostrando as {MAIS_RECENTES} mais recentes de {lista.carga.dados.total}.
        </p>
      )}
    </>
  )
}
