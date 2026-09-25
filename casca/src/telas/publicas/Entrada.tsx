import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Button, Checkbox, FormField, Input, Spinner } from '@/components/ui'
import { Link } from '../../componentes/Link'
import { CampoDeCodigo, codigoCompleto, ListaDeCodigos, QrCodeDoSegredo } from '../../componentes/SegundoFator'
import {
  concluirComCodigo,
  confirmarCadastroDoSegundoFator,
  ErroDeEntrada,
  entrar,
  iniciarCadastroDoSegundoFator,
  motivoDoUltimoFim,
  type CadastroConcluido,
} from '../../plataforma/sessao'
import type { CadastroDeSegundoFator } from '../../plataforma/tipos'
import { TelaPublica } from './TelaPublica'

/**
 * As etapas da entrada: senha; depois, com a verificação em duas etapas (RF10), o código do
 * aplicativo — ou, no primeiro acesso, o cadastro do aplicativo e os códigos de recuperação.
 */
type Etapa =
  | { tipo: 'senha' }
  | { tipo: 'codigo'; desafio: string }
  | { tipo: 'cadastro'; desafio: string }
  | { tipo: 'codigos'; concluido: CadastroConcluido }

const ERRO = 'mt-4 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-500/15 dark:text-red-300'

/** Login da plataforma — o único do sistema: nenhum módulo tem tela de login (Contrato §4.1). */
export function Entrada() {
  const [etapa, setEtapa] = useState<Etapa>({ tipo: 'senha' })
  const [email, setEmail] = useState('')
  const [aviso, setAviso] = useState<string | null>(null)

  /** O desafio venceu (5 minutos) ou morreu por erros: volta à senha, explicando. */
  const recomecar = useCallback((mensagem: string) => {
    setAviso(mensagem)
    setEtapa({ tipo: 'senha' })
  }, [])

  return (
    <TelaPublica>
      {etapa.tipo === 'senha' && (
        <EtapaDaSenha
          email={email}
          aoMudarEmail={setEmail}
          aviso={aviso}
          aoEntrar={(desafio) => {
            setAviso(null)
            setEtapa(desafio.etapa === 'cadastro_segundo_fator'
              ? { tipo: 'cadastro', desafio: desafio.desafio }
              : { tipo: 'codigo', desafio: desafio.desafio })
          }}
        />
      )}
      {etapa.tipo === 'codigo' && <EtapaDoCodigo desafio={etapa.desafio} aoExpirar={recomecar} aoVoltar={() => recomecar('')} />}
      {etapa.tipo === 'cadastro' && (
        <EtapaDoCadastro desafio={etapa.desafio} aoExpirar={recomecar} aoConcluir={(concluido) => setEtapa({ tipo: 'codigos', concluido })} />
      )}
      {etapa.tipo === 'codigos' && <EtapaDosCodigos concluido={etapa.concluido} email={email} />}
    </TelaPublica>
  )
}

function EtapaDaSenha({
  email,
  aoMudarEmail,
  aviso,
  aoEntrar,
}: {
  email: string
  aoMudarEmail: (email: string) => void
  aviso: string | null
  aoEntrar: (desafio: { etapa: string; desafio: string }) => void
}) {
  const [senha, setSenha] = useState('')
  const [erro, setErro] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)
  const motivo = aviso || motivoDoUltimoFim()

  async function enviar(evento: FormEvent) {
    evento.preventDefault()
    setEnviando(true)
    setErro(null)
    try {
      const resultado = await entrar(email.trim(), senha)
      if (resultado.tipo === 'desafio') aoEntrar(resultado.desafio)
    } catch (e) {
      setErro(e instanceof ErroDeEntrada ? e.message : 'Não foi possível entrar.')
      setSenha('')
    } finally {
      setEnviando(false)
    }
  }

  return (
    <form onSubmit={enviar} noValidate>
      <h1 className="text-2xl font-bold tracking-tight text-titulo">Entrar</h1>
      <p className="mt-1 text-sm text-texto-3">Use o e-mail e a senha da sua conta.</p>

      {motivo && !erro && (
        <p role="status" className="mt-5 rounded-lg bg-brand-100 px-3 py-2 text-sm text-brand-800 dark:bg-brand-800/30 dark:text-brand-300">
          {motivo}
        </p>
      )}

      <div className="mt-6 grid gap-4">
        <FormField label="E-mail" htmlFor="entrada-email">
          <Input
            id="entrada-email"
            type="email"
            autoComplete="username"
            autoFocus
            required
            value={email}
            onChange={(e) => aoMudarEmail(e.target.value)}
          />
        </FormField>
        <FormField label="Senha" htmlFor="entrada-senha">
          <Input
            id="entrada-senha"
            type="password"
            autoComplete="current-password"
            required
            value={senha}
            onChange={(e) => setSenha(e.target.value)}
          />
        </FormField>
      </div>

      <div className="mt-2 text-right">
        <Link href="/esqueci-senha" className="text-xs font-medium text-brand-700 hover:underline dark:text-brand-400">
          Esqueci minha senha
        </Link>
      </div>

      {erro && <p role="alert" className={ERRO}>{erro}</p>}

      <Button type="submit" size="lg" loading={enviando} disabled={!email.trim() || !senha} className="mt-6 w-full">
        {enviando ? 'Entrando…' : 'Entrar'}
      </Button>
    </form>
  )
}

function EtapaDoCodigo({ desafio, aoExpirar, aoVoltar }: { desafio: string; aoExpirar: (mensagem: string) => void; aoVoltar: () => void }) {
  const [porRecuperacao, setPorRecuperacao] = useState(false)
  const [codigo, setCodigo] = useState('')
  const [erro, setErro] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)
  const pronto = porRecuperacao ? codigo.replace(/[\s-]/g, '').length === 10 : codigoCompleto(codigo)

  async function enviar(evento: FormEvent) {
    evento.preventDefault()
    if (!pronto) return
    setEnviando(true)
    setErro(null)
    try {
      await concluirComCodigo(desafio, porRecuperacao ? { codigoRecuperacao: codigo.trim() } : { codigo: codigo.trim() })
    } catch (e) {
      if (e instanceof ErroDeEntrada && e.codigo === 'DESAFIO_EXPIRADO') return aoExpirar(e.message)
      setErro(e instanceof ErroDeEntrada ? e.message : 'Não foi possível entrar.')
      setCodigo('')
    } finally {
      setEnviando(false)
    }
  }

  return (
    <form onSubmit={enviar} noValidate>
      <h1 className="text-2xl font-bold tracking-tight text-titulo">Verificação em duas etapas</h1>
      <p className="mt-1 text-sm text-texto-3">
        {porRecuperacao
          ? 'Digite um dos códigos de recuperação que você guardou. Cada um vale uma vez.'
          : 'Digite o código de seis dígitos do aplicativo autenticador.'}
      </p>

      <div className="mt-6">
        {porRecuperacao ? (
          <FormField label="Código de recuperação" htmlFor="entrada-recuperacao">
            <Input
              id="entrada-recuperacao"
              autoComplete="off"
              autoFocus
              maxLength={11}
              placeholder="ABCDE-FGHJK"
              value={codigo}
              onChange={(e) => setCodigo(e.target.value.toUpperCase())}
              className="font-mono tracking-wider"
            />
          </FormField>
        ) : (
          <FormField label="Código" htmlFor="entrada-codigo">
            <CampoDeCodigo id="entrada-codigo" valor={codigo} aoMudar={setCodigo} invalido={!!erro} />
          </FormField>
        )}
      </div>

      {erro && <p role="alert" className={ERRO}>{erro}</p>}

      <Button type="submit" size="lg" loading={enviando} disabled={!pronto} className="mt-6 w-full">
        {enviando ? 'Conferindo…' : 'Entrar'}
      </Button>

      <div className="mt-4 flex justify-between text-xs">
        <button type="button" onClick={aoVoltar} className="font-medium text-texto-3 hover:underline">
          Voltar
        </button>
        <button
          type="button"
          onClick={() => {
            setPorRecuperacao((atual) => !atual)
            setCodigo('')
            setErro(null)
          }}
          className="font-medium text-brand-700 hover:underline dark:text-brand-400"
        >
          {porRecuperacao ? 'Usar o aplicativo' : 'Perdi o celular — usar um código de recuperação'}
        </button>
      </div>
    </form>
  )
}

function EtapaDoCadastro({
  desafio,
  aoExpirar,
  aoConcluir,
}: {
  desafio: string
  aoExpirar: (mensagem: string) => void
  aoConcluir: (concluido: CadastroConcluido) => void
}) {
  const [cadastro, setCadastro] = useState<CadastroDeSegundoFator | null>(null)
  const [codigo, setCodigo] = useState('')
  const [erro, setErro] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  useEffect(() => {
    iniciarCadastroDoSegundoFator(desafio)
      .then(setCadastro)
      .catch((e: unknown) => {
        if (e instanceof ErroDeEntrada && e.codigo === 'DESAFIO_EXPIRADO') aoExpirar(e.message)
        else setErro(e instanceof ErroDeEntrada ? e.message : 'Não foi possível começar o cadastro.')
      })
  }, [desafio, aoExpirar])

  async function enviar(evento: FormEvent) {
    evento.preventDefault()
    if (!codigoCompleto(codigo)) return
    setEnviando(true)
    setErro(null)
    try {
      aoConcluir(await confirmarCadastroDoSegundoFator(desafio, codigo.trim()))
    } catch (e) {
      if (e instanceof ErroDeEntrada && e.codigo === 'DESAFIO_EXPIRADO') return aoExpirar(e.message)
      setErro(e instanceof ErroDeEntrada ? e.message : 'Não foi possível confirmar.')
      setCodigo('')
    } finally {
      setEnviando(false)
    }
  }

  return (
    <form onSubmit={enviar} noValidate>
      <h1 className="text-2xl font-bold tracking-tight text-titulo">Proteja a sua conta</h1>
      <p className="mt-1 text-sm text-texto-3">
        A plataforma pede a verificação em duas etapas. Abra um aplicativo autenticador (Google Authenticator,
        Microsoft Authenticator, Authy…), leia o QR Code e digite o código que aparecer.
      </p>

      <div className="mt-6">{cadastro ? <QrCodeDoSegredo cadastro={cadastro} /> : !erro && <Spinner />}</div>

      <div className="mt-6">
        <FormField label="Código do aplicativo" htmlFor="cadastro-codigo">
          <CampoDeCodigo id="cadastro-codigo" valor={codigo} aoMudar={setCodigo} invalido={!!erro} />
        </FormField>
      </div>

      {erro && <p role="alert" className={ERRO}>{erro}</p>}

      <Button type="submit" size="lg" loading={enviando} disabled={!cadastro || !codigoCompleto(codigo)} className="mt-6 w-full">
        {enviando ? 'Conferindo…' : 'Ativar e entrar'}
      </Button>
    </form>
  )
}

function EtapaDosCodigos({ concluido, email }: { concluido: CadastroConcluido; email: string }) {
  const [guardou, setGuardou] = useState(false)
  return (
    <div>
      <h1 className="text-2xl font-bold tracking-tight text-titulo">Guarde os códigos de recuperação</h1>
      <p className="mt-1 text-sm text-texto-3">
        Se você perder o celular, cada código abaixo permite entrar uma vez. Eles não aparecem de novo: copie, baixe ou
        anote agora, e guarde longe do celular.
      </p>
      <div className="mt-6">
        <ListaDeCodigos codigos={concluido.codigosRecuperacao} email={email.trim()} />
      </div>
      <label className="mt-6 flex items-center gap-2 text-sm text-texto">
        <Checkbox checked={guardou} onChange={(e) => setGuardou(e.target.checked)} />
        Guardei os códigos num lugar seguro
      </label>
      <Button size="lg" disabled={!guardou} onClick={concluido.continuar} className="mt-6 w-full">
        Continuar
      </Button>
    </div>
  )
}
