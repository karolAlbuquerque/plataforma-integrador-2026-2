import { useState, type FormEvent } from 'react'
import { Button, FormField, Input } from '@/components/ui'
import { Link } from '../../componentes/Link'
import { ErroDeEntrada, entrar, motivoDoUltimoFim } from '../../plataforma/sessao'
import { TelaPublica } from './TelaPublica'

/** Login da plataforma — o único do sistema: nenhum módulo tem tela de login (Contrato §4.1). */
export function Entrada() {
  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [erro, setErro] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)
  const motivo = motivoDoUltimoFim()

  async function enviar(evento: FormEvent) {
    evento.preventDefault()
    setEnviando(true)
    setErro(null)
    try {
      await entrar(email.trim(), senha)
    } catch (e) {
      setErro(e instanceof ErroDeEntrada ? e.message : 'Não foi possível entrar.')
      setSenha('')
    } finally {
      setEnviando(false)
    }
  }

  return (
    <TelaPublica>
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
              onChange={(e) => setEmail(e.target.value)}
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

        {erro && (
          <p role="alert" className="mt-4 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-500/15 dark:text-red-300">
            {erro}
          </p>
        )}

        <Button type="submit" size="lg" loading={enviando} disabled={!email.trim() || !senha} className="mt-6 w-full">
          {enviando ? 'Entrando…' : 'Entrar'}
        </Button>
      </form>
    </TelaPublica>
  )
}
