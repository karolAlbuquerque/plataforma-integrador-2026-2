import { useEffect, useState } from 'react'
import { Check, Copy, Download } from 'lucide-react'
import { Button, Input, Spinner } from '@/components/ui'
import type { CadastroDeSegundoFator } from '../plataforma/tipos'

/**
 * Peças da verificação em duas etapas (RF10), usadas na entrada, no fim da sessão e em Minha
 * conta. O QR Code é gerado aqui, no navegador: o segredo não sai para nenhum serviço de terceiros.
 * A biblioteca é carregada só quando o QR aparece, que é raro, para não pesar na casca.
 */

/** O QR Code e, para quem não consegue ler, o segredo em grupos de quatro. */
export function QrCodeDoSegredo({ cadastro }: { cadastro: CadastroDeSegundoFator }) {
  const [imagem, setImagem] = useState<string | null>(null)

  useEffect(() => {
    let ativo = true
    import('qrcode')
      .then(({ default: QRCode }) => QRCode.toDataURL(cadastro.uri, { margin: 1, width: 200, errorCorrectionLevel: 'M' }))
      .then((url) => ativo && setImagem(url))
      .catch(() => ativo && setImagem(null))
    return () => {
      ativo = false
    }
  }, [cadastro.uri])

  return (
    <div className="flex flex-col items-center gap-3">
      <div className="flex size-[200px] items-center justify-center rounded-lg bg-white p-1 ring-1 ring-borda">
        {imagem ? <img src={imagem} alt="QR Code para o aplicativo autenticador" width={200} height={200} /> : <Spinner />}
      </div>
      <div className="text-center">
        <p className="text-xs text-texto-3">Não consegue ler? Digite este código no aplicativo:</p>
        <p className="mt-1 flex items-center justify-center gap-1 font-mono text-sm font-semibold tracking-wider text-titulo">
          <span data-testid="segredo-do-segundo-fator">{cadastro.segredo.match(/.{1,4}/g)?.join(' ')}</span>
          <BotaoCopiar texto={cadastro.segredo} rotulo="Copiar o código" />
        </p>
      </div>
    </div>
  )
}

/** Seis dígitos, com o preenchimento automático do celular (one-time-code). */
export function CampoDeCodigo({ id, valor, aoMudar, invalido, focar = true }: {
  id: string
  valor: string
  aoMudar: (valor: string) => void
  invalido?: boolean
  focar?: boolean
}) {
  return (
    <Input
      id={id}
      inputMode="numeric"
      autoComplete="one-time-code"
      autoFocus={focar}
      maxLength={7}
      placeholder="000000"
      value={valor}
      invalid={invalido}
      onChange={(e) => aoMudar(e.target.value.replace(/[^\d ]/g, ''))}
      className="text-center font-mono text-lg tracking-[0.4em]"
    />
  )
}

export const codigoCompleto = (codigo: string) => codigo.replace(/\s/g, '').length === 6

/** Os dez códigos de recuperação, que o usuário vê uma vez só. */
export function ListaDeCodigos({ codigos, email }: { codigos: string[]; email: string }) {
  const texto = codigos.join('\n')
  return (
    <div className="flex flex-col gap-3">
      <ol data-testid="codigos-de-recuperacao" className="grid grid-cols-2 gap-2 rounded-lg bg-superficie-2 p-4 font-mono text-sm text-titulo">
        {codigos.map((codigo) => (
          <li key={codigo}>{codigo}</li>
        ))}
      </ol>
      <div className="flex flex-wrap gap-2">
        <BotaoCopiar texto={texto} rotulo="Copiar todos" comTexto />
        <Button
          variant="outline"
          size="sm"
          onClick={() =>
            baixarTexto(
              'codigos-de-recuperacao.txt',
              `Códigos de recuperação de ${email}\nCada um vale uma vez. Guarde num lugar seguro.\n\n${texto}\n`,
            )
          }
        >
          <Download aria-hidden className="size-4" /> Baixar
        </Button>
      </div>
    </div>
  )
}

function BotaoCopiar({ texto, rotulo, comTexto = false }: { texto: string; rotulo: string; comTexto?: boolean }) {
  const [copiado, setCopiado] = useState(false)

  async function copiar() {
    try {
      await navigator.clipboard.writeText(texto)
      setCopiado(true)
      window.setTimeout(() => setCopiado(false), 2_000)
    } catch {
      // sem permissão de área de transferência: o texto continua na tela
    }
  }

  const Icone = copiado ? Check : Copy
  if (comTexto) {
    return (
      <Button variant="outline" size="sm" onClick={() => void copiar()}>
        <Icone aria-hidden className="size-4" /> {copiado ? 'Copiado' : rotulo}
      </Button>
    )
  }
  return (
    <button type="button" onClick={() => void copiar()} aria-label={rotulo} title={rotulo} className="rounded p-1 text-texto-3 hover:bg-superficie-2 hover:text-titulo">
      <Icone aria-hidden className="size-4" />
    </button>
  )
}

export function baixarTexto(nome: string, conteudo: string, tipo = 'text/plain;charset=utf-8') {
  const endereco = URL.createObjectURL(new Blob([conteudo], { type: tipo }))
  const link = document.createElement('a')
  link.href = endereco
  link.download = nome
  document.body.append(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(endereco), 1_000)
}
