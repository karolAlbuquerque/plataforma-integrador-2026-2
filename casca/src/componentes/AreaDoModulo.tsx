import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { LoaderCircle, PlugZap, RotateCw, SearchX } from 'lucide-react'
import { avisar } from '../plataforma/avisos'
import { NIVEIS_DE_AVISO, type MensagemDoModulo, type MensagemParaModulo } from '../plataforma/mensagens'
import { refletirRotaDoModulo } from '../plataforma/rotas'
import { renovar, type Sessao } from '../plataforma/sessao'
import type { NivelDeAviso, ModuloDoMenu, Tema } from '../plataforma/tipos'

const ESPERA_PELO_MODULO = 15_000
/** Token emitido há menos de um minuto: reenviar em vez de renovar de novo. */
const TOKEN_RECENTE = 14 * 60_000

interface Props {
  modulo: ModuloDoMenu | undefined
  carregandoMenu: boolean
  subrota: string
  versao: number
  sessao: Sessao
  tema: Tema
}

/**
 * O módulo roda num iframe servido pela mesma origem, em /modulos/{codigo}/ (Contrato §12.8).
 * A casca entrega a sessão por postMessage quando o módulo avisa que está pronto, e reenvia o
 * token a cada renovação — o módulo nunca faz login nem guarda token.
 */
export function AreaDoModulo({ modulo, carregandoMenu, subrota, versao, sessao, tema }: Props) {
  const [abrirMesmoAssim, setAbrirMesmoAssim] = useState(false)

  if (!modulo) {
    if (carregandoMenu) return <Centro><LoaderCircle aria-hidden className="size-5 animate-spin text-muted-foreground" /></Centro>
    return (
      <Centro>
        <SearchX aria-hidden className="size-8 text-muted-foreground" />
        <h1 className="mt-3 font-medium">Módulo não encontrado</h1>
        <p className="mt-1 max-w-sm text-center text-sm text-muted-foreground">
          Ele não existe ou o seu perfil não tem acesso a ele.
        </p>
      </Centro>
    )
  }

  if (!modulo.disponivel && !abrirMesmoAssim) {
    return (
      <Centro>
        <PlugZap aria-hidden className="size-8 text-muted-foreground" />
        <h1 className="mt-3 font-medium">{modulo.nome} está indisponível</h1>
        <p className="mt-1 max-w-sm text-center text-sm text-muted-foreground">
          O módulo não respondeu à última verificação da plataforma. Os outros módulos seguem funcionando.
        </p>
        <button
          type="button"
          onClick={() => setAbrirMesmoAssim(true)}
          className="mt-4 rounded-md border px-3 py-2 text-sm font-medium hover:bg-accent"
        >
          Tentar abrir mesmo assim
        </button>
      </Centro>
    )
  }

  return <Quadro key={modulo.codigo} modulo={modulo} subrota={subrota} versao={versao} sessao={sessao} tema={tema} />
}

function Quadro({ modulo, subrota, versao, sessao, tema }: Omit<Props, 'modulo' | 'carregandoMenu'> & { modulo: ModuloDoMenu }) {
  const quadro = useRef<HTMLIFrameElement>(null)
  const [pronto, setPronto] = useState(false)
  const [demorou, setDemorou] = useState(false)
  const [altura, setAltura] = useState<number | null>(null)
  const [recargas, setRecargas] = useState(0)

  // O mais recente de cada um, para o tratador de mensagens não precisar ser recriado
  const atual = useRef({ sessao, tema, pronto })
  atual.current = { sessao, tema, pronto }

  // Só a navegação feita pela casca (versão nova) muda o endereço do iframe. A feita por dentro
  // do módulo chega por modulo:navegar e só atualiza a URL da casca — por isso subrota fica de fora.
  const endereco = useMemo(() => modulo.urlFrontend + subrota.replace(/^\//, ''), [modulo.urlFrontend, versao])

  const enviar = useCallback((mensagem: MensagemParaModulo) => {
    quadro.current?.contentWindow?.postMessage(mensagem, window.location.origin)
  }, [])

  useEffect(() => {
    setPronto(false)
    setDemorou(false)
    setAltura(null)
    const espera = window.setTimeout(() => setDemorou(true), ESPERA_PELO_MODULO)
    return () => window.clearTimeout(espera)
  }, [endereco, recargas])

  useEffect(() => {
    function aoReceber(evento: MessageEvent) {
      // Só a própria plataforma, e só a janela deste iframe (Contrato §12.2)
      if (evento.origin !== window.location.origin || evento.source !== quadro.current?.contentWindow) return
      const mensagem = evento.data as MensagemDoModulo | null
      switch (mensagem?.tipo) {
        case 'modulo:pronto': {
          const { sessao: s, tema: t } = atual.current
          setPronto(true)
          enviar({ tipo: 'plataforma:sessao', token: s.token, tenantId: s.usuario.tenantId, usuario: s.usuario, tema: t })
          break
        }
        case 'modulo:altura':
          if (typeof mensagem.altura === 'number' && Number.isFinite(mensagem.altura)) {
            setAltura(Math.min(Math.max(Math.round(mensagem.altura), 0), 100_000))
          }
          break
        case 'modulo:navegar':
          if (typeof mensagem.rota === 'string') refletirRotaDoModulo(modulo.codigo, mensagem.rota)
          break
        case 'modulo:token-expirado': {
          const s = atual.current.sessao
          if (s.expiraEm - Date.now() > TOKEN_RECENTE) enviar({ tipo: 'plataforma:token', token: s.token })
          else void renovar()   // o token novo segue pelo efeito abaixo
          break
        }
        case 'modulo:notificar':
          if (NIVEIS_DE_AVISO.has(mensagem.nivel) && typeof mensagem.texto === 'string' && mensagem.texto.trim()) {
            avisar(mensagem.nivel as NivelDeAviso, mensagem.texto)
          }
          break
      }
    }
    window.addEventListener('message', aoReceber)
    return () => window.removeEventListener('message', aoReceber)
  }, [enviar, modulo.codigo])

  useEffect(() => {
    if (atual.current.pronto) enviar({ tipo: 'plataforma:token', token: sessao.token })
  }, [enviar, sessao.token])

  useEffect(() => {
    if (atual.current.pronto) enviar({ tipo: 'plataforma:tema', tema })
  }, [enviar, tema])

  return (
    <div className="relative">
      {!pronto && (
        <div className="absolute inset-0 z-10 grid place-items-center bg-background">
          {demorou ? (
            <div className="flex max-w-sm flex-col items-center px-4 text-center">
              <PlugZap aria-hidden className="size-8 text-muted-foreground" />
              <p className="mt-3 font-medium">{modulo.nome} não respondeu</p>
              <p className="mt-1 text-sm text-muted-foreground">
                O módulo pode estar fora do ar ou ainda não conversa com a plataforma.
              </p>
              <button
                type="button"
                onClick={() => setRecargas((n) => n + 1)}
                className="mt-4 inline-flex items-center gap-2 rounded-md border px-3 py-2 text-sm font-medium hover:bg-accent"
              >
                <RotateCw aria-hidden className="size-4" /> Recarregar
              </button>
            </div>
          ) : (
            <p className="flex items-center gap-2 text-sm text-muted-foreground" role="status">
              <LoaderCircle aria-hidden className="size-4 animate-spin" /> Carregando {modulo.nome}…
            </p>
          )}
        </div>
      )}
      <iframe
        key={`${endereco}#${recargas}`}
        ref={quadro}
        src={endereco}
        title={modulo.nome}
        className="block w-full border-0"
        style={{ height: altura ?? undefined, minHeight: 'calc(100dvh - 3.5rem)' }}
      />
    </div>
  )
}

function Centro({ children }: { children: React.ReactNode }) {
  return <div className="flex min-h-[calc(100dvh-3.5rem)] flex-col items-center justify-center p-6">{children}</div>
}
