import { useState, type FormEvent } from 'react'
import { LoaderCircle } from 'lucide-react'
import { ErroDeEntrada, entrar, motivoDoUltimoFim } from '../plataforma/sessao'
import { Marca } from './Marca'

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
    <main className="grid min-h-dvh place-items-center bg-muted/40 px-4 py-10">
      <div className="w-full max-w-sm">
        <Marca className="mb-8 justify-center" />
        <form onSubmit={enviar} className="rounded-lg border bg-card p-6 shadow-sm" noValidate>
          <h1 className="text-lg font-semibold">Entrar</h1>
          <p className="mt-1 text-sm text-muted-foreground">Use o e-mail e a senha da sua conta.</p>

          {motivo && !erro && (
            <p role="status" className="mt-4 rounded-md bg-secondary px-3 py-2 text-sm text-secondary-foreground">
              {motivo}
            </p>
          )}

          <div className="mt-5 grid gap-4">
            <label className="grid gap-1.5 text-sm font-medium">
              E-mail
              <input
                type="email"
                autoComplete="username"
                required
                autoFocus
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="h-10 rounded-md border border-input bg-background px-3 font-normal outline-none focus-visible:ring-2 focus-visible:ring-ring"
              />
            </label>
            <label className="grid gap-1.5 text-sm font-medium">
              Senha
              <input
                type="password"
                autoComplete="current-password"
                required
                value={senha}
                onChange={(e) => setSenha(e.target.value)}
                className="h-10 rounded-md border border-input bg-background px-3 font-normal outline-none focus-visible:ring-2 focus-visible:ring-ring"
              />
            </label>
          </div>

          {erro && (
            <p role="alert" className="mt-4 text-sm text-destructive">
              {erro}
            </p>
          )}

          <button
            type="submit"
            disabled={enviando || !email.trim() || !senha}
            className="mt-6 inline-flex h-10 w-full items-center justify-center gap-2 rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground transition-opacity hover:opacity-90 disabled:opacity-60"
          >
            {enviando && <LoaderCircle aria-hidden className="size-4 animate-spin" />}
            {enviando ? 'Entrando…' : 'Entrar'}
          </button>
        </form>
      </div>
    </main>
  )
}
