import { useEffect, useState, type FormEvent } from 'react'
import { CircleCheck, Eye, EyeOff, LinkIcon } from 'lucide-react'
import { Button, FormField, Input, Spinner } from '@/components/ui'
import { Link } from '../../componentes/Link'
import { ErroDaApi, mensagemDe } from '../../plataforma/api'
import { dataHora, senhaAceitavel } from '../../plataforma/formato'
import { navegar } from '../../plataforma/rotas'
import { definirSenha, verificarLink } from '../../plataforma/senha'
import type { LinkVerificado } from '../../plataforma/tipos'
import { RegrasDaSenha, TelaPublica } from './TelaPublica'

/** O token vem no fragmento (#token=...), que o navegador não envia ao servidor nem põe no Referer. */
function lerToken() {
  const token = new URLSearchParams(window.location.hash.replace(/^#/, '')).get('token')
  return token && /^[A-Za-z0-9_-]{20,100}$/.test(token) ? token : null
}

type Estado =
  | { tipo: 'verificando' }
  | { tipo: 'invalido'; mensagem: string }
  | { tipo: 'pronto'; link: LinkVerificado }
  | { tipo: 'definida' }

/** Convite e recuperação terminam aqui: a pessoa define a própria senha (RF07, RF14). */
export function DefinirSenha() {
  // Lido uma vez só: logo depois o fragmento sai da barra de endereço e do histórico
  const [token] = useState(lerToken)
  const [estado, setEstado] = useState<Estado>(() =>
    token ? { tipo: 'verificando' } : { tipo: 'invalido', mensagem: 'O link está incompleto. Abra de novo o link do e-mail.' })

  useEffect(() => {
    if (window.location.hash) window.history.replaceState(null, '', window.location.pathname)
  }, [])

  useEffect(() => {
    if (!token) return
    let ativo = true
    verificarLink(token)
      .then((link) => ativo && setEstado({ tipo: 'pronto', link }))
      .catch((erro) => ativo && setEstado({ tipo: 'invalido', mensagem: mensagemDe(erro) }))
    return () => {
      ativo = false
    }
  }, [token])

  return (
    <TelaPublica>
      {estado.tipo === 'verificando' && <Spinner label="Conferindo o link" />}
      {estado.tipo === 'invalido' && (
        <div role="alert">
          <span className="grid size-12 place-items-center rounded-full bg-red-50 text-red-600 dark:bg-red-500/15 dark:text-red-300">
            <LinkIcon aria-hidden className="size-6" />
          </span>
          <h1 className="mt-4 text-2xl font-bold tracking-tight text-titulo">Link inválido</h1>
          <p className="mt-2 text-sm text-texto-2">{estado.mensagem}</p>
          <p className="mt-2 text-sm text-texto-3">
            Esqueceu a senha? Peça um link novo. Recebeu um convite? Peça ao administrador para reenviá-lo.
          </p>
          <div className="mt-6 flex flex-wrap gap-2">
            <Button onClick={() => navegar('/esqueci-senha')}>Pedir um link novo</Button>
            <Button variant="outline" onClick={() => navegar('/')}>Ir para a entrada</Button>
          </div>
        </div>
      )}
      {estado.tipo === 'pronto' && token && (
        <Formulario token={token} link={estado.link} aoDefinir={() => setEstado({ tipo: 'definida' })}
          aoInvalidar={(mensagem) => setEstado({ tipo: 'invalido', mensagem })} />
      )}
      {estado.tipo === 'definida' && (
        <div role="status">
          <span className="grid size-12 place-items-center rounded-full bg-brand-100 text-brand-800 dark:bg-brand-800/30 dark:text-brand-300">
            <CircleCheck aria-hidden className="size-6" />
          </span>
          <h1 className="mt-4 text-2xl font-bold tracking-tight text-titulo">Senha definida</h1>
          <p className="mt-2 text-sm text-texto-2">Entre com o seu e-mail e a senha nova.</p>
          <Button size="lg" className="mt-6 w-full" onClick={() => navegar('/')}>Ir para a entrada</Button>
        </div>
      )}
    </TelaPublica>
  )
}

function Formulario({ token, link, aoDefinir, aoInvalidar }: {
  token: string
  link: LinkVerificado
  aoDefinir: () => void
  aoInvalidar: (mensagem: string) => void
}) {
  const [senha, setSenha] = useState('')
  const [confirmacao, setConfirmacao] = useState('')
  const [visivel, setVisivel] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)
  const diferentes = confirmacao.length > 0 && confirmacao !== senha
  const primeiroNome = link.nome.split(' ')[0]

  async function enviar(evento: FormEvent) {
    evento.preventDefault()
    setEnviando(true)
    setErro(null)
    try {
      await definirSenha(token, senha)
      aoDefinir()
    } catch (e) {
      if (e instanceof ErroDaApi && e.codigo === 'LINK_INVALIDO') aoInvalidar(e.message)
      else setErro(e instanceof ErroDaApi ? e.porCampo.novaSenha ?? e.message : mensagemDe(e))
    } finally {
      setEnviando(false)
    }
  }

  return (
    <form onSubmit={enviar} noValidate>
      <h1 className="text-2xl font-bold tracking-tight text-titulo">
        {link.tipo === 'convite' ? `Boas-vindas, ${primeiroNome}` : 'Crie uma senha nova'}
      </h1>
      <p className="mt-1 text-sm text-texto-3">
        {link.tipo === 'convite' ? 'Crie a senha para entrar como ' : 'Para a conta '}
        <strong className="font-semibold text-texto-2">{link.email}</strong>. O link vale até {dataHora(link.expiraEm)}.
      </p>

      {/* O e-mail no formulário ajuda o gerenciador de senhas a guardar a senha nova na conta certa */}
      <input type="email" autoComplete="username" value={link.email} readOnly hidden />

      <div className="mt-6 grid gap-4">
        <FormField label="Senha nova" htmlFor="nova-senha">
          <div className="relative">
            <Input
              id="nova-senha"
              type={visivel ? 'text' : 'password'}
              autoComplete="new-password"
              autoFocus
              value={senha}
              onChange={(e) => setSenha(e.target.value)}
              className="pr-10"
            />
            <button
              type="button"
              onClick={() => setVisivel((v) => !v)}
              aria-label={visivel ? 'Esconder a senha' : 'Mostrar a senha'}
              className="absolute top-1/2 right-2 -translate-y-1/2 rounded-lg p-1 text-texto-3 hover:text-texto"
            >
              {visivel ? <EyeOff aria-hidden className="size-4" /> : <Eye aria-hidden className="size-4" />}
            </button>
          </div>
        </FormField>
        <RegrasDaSenha senha={senha} />
        <FormField label="Repita a senha" htmlFor="confirmar-senha" error={diferentes ? 'As duas senhas não são iguais.' : null}>
          <Input
            id="confirmar-senha"
            type={visivel ? 'text' : 'password'}
            autoComplete="new-password"
            invalid={diferentes}
            value={confirmacao}
            onChange={(e) => setConfirmacao(e.target.value)}
          />
        </FormField>
      </div>

      {erro && (
        <p role="alert" className="mt-4 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-500/15 dark:text-red-300">
          {erro}
        </p>
      )}

      <Button type="submit" size="lg" loading={enviando} disabled={!senhaAceitavel(senha) || senha !== confirmacao} className="mt-6 w-full">
        Definir a senha
      </Button>
      <Link href="/" className="mt-4 inline-block text-sm font-medium text-brand-700 hover:underline dark:text-brand-400">
        Voltar para a entrada
      </Link>
    </form>
  )
}
