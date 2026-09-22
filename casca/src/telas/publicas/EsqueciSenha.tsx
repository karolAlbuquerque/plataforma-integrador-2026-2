import { useState, type FormEvent } from 'react'
import { ArrowLeft, MailCheck } from 'lucide-react'
import { Button, FormField, Input } from '@/components/ui'
import { Link } from '../../componentes/Link'
import { ErroDaApi, mensagemDe } from '../../plataforma/api'
import { pedirRecuperacao } from '../../plataforma/senha'
import { TelaPublica } from './TelaPublica'

/**
 * Pedido de recuperação (RF07). A resposta é a mesma exista o e-mail ou não — a tela também não
 * pode revelar nada: sempre a mesma confirmação.
 */
export function EsqueciSenha() {
  const [email, setEmail] = useState('')
  const [erroDoCampo, setErroDoCampo] = useState<string | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)
  const [enviado, setEnviado] = useState(false)

  async function enviar(evento: FormEvent) {
    evento.preventDefault()
    setEnviando(true)
    setErro(null)
    setErroDoCampo(null)
    try {
      await pedirRecuperacao(email.trim())
      setEnviado(true)
    } catch (e) {
      if (e instanceof ErroDaApi && e.status === 400) setErroDoCampo(e.porCampo.email ?? e.message)
      else setErro(mensagemDe(e))
    } finally {
      setEnviando(false)
    }
  }

  return (
    <TelaPublica>
      {enviado ? (
        <div role="status">
          <span className="grid size-12 place-items-center rounded-full bg-brand-100 text-brand-800 dark:bg-brand-800/30 dark:text-brand-300">
            <MailCheck aria-hidden className="size-6" />
          </span>
          <h1 className="mt-4 text-2xl font-bold tracking-tight text-titulo">Confira o seu e-mail</h1>
          <p className="mt-2 text-sm text-texto-2">
            Se <strong className="font-semibold">{email.trim()}</strong> estiver cadastrado, enviamos um link para criar uma
            senha nova. Ele vale por 30 minutos e só pode ser usado uma vez.
          </p>
          <p className="mt-2 text-sm text-texto-3">Não chegou em alguns minutos? Confira a caixa de spam.</p>
          <Link href="/" className="mt-6 inline-flex items-center gap-1.5 text-sm font-medium text-brand-700 hover:underline dark:text-brand-400">
            <ArrowLeft aria-hidden className="size-4" /> Voltar para a entrada
          </Link>
        </div>
      ) : (
        <form onSubmit={enviar} noValidate>
          <h1 className="text-2xl font-bold tracking-tight text-titulo">Esqueci minha senha</h1>
          <p className="mt-1 text-sm text-texto-3">Informe o e-mail da sua conta. Mandaremos um link para criar uma senha nova.</p>

          <div className="mt-6">
            <FormField label="E-mail" htmlFor="recuperar-email" error={erroDoCampo}>
              <Input
                id="recuperar-email"
                type="email"
                autoComplete="username"
                autoFocus
                required
                invalid={!!erroDoCampo}
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
            </FormField>
          </div>

          {erro && (
            <p role="alert" className="mt-4 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-500/15 dark:text-red-300">
              {erro}
            </p>
          )}

          <Button type="submit" size="lg" loading={enviando} disabled={!email.trim()} className="mt-6 w-full">
            Enviar o link
          </Button>
          <Link href="/" className="mt-4 inline-flex items-center gap-1.5 text-sm font-medium text-brand-700 hover:underline dark:text-brand-400">
            <ArrowLeft aria-hidden className="size-4" /> Voltar para a entrada
          </Link>
        </form>
      )}
    </TelaPublica>
  )
}
