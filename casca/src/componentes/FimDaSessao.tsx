import { useEffect, useState, type FormEvent } from 'react'
import { Button, FormField, Input, Modal } from '@/components/ui'
import { useCasca } from '../plataforma/contexto'
import { aoMudarFimDaSessao, entrar, ErroDeEntrada, fimDaSessaoAtual, sair, type FimDaSessao } from '../plataforma/sessao'

/**
 * RF41: a sessão dura oito horas. Cinco minutos antes, e de novo quando ela acaba, a casca pede a
 * senha num modal — sem sair da tela: o iframe do módulo continua montado com o que o usuário
 * preenchia, e recebe o token novo pelo plataforma:token de sempre.
 */
export function AvisoDeFimDaSessao() {
  const { sessao } = useCasca()
  const [fim, setFim] = useState<FimDaSessao>(fimDaSessaoAtual)
  const [dispensadoEm, setDispensadoEm] = useState<number | null>(null)
  const [senha, setSenha] = useState('')
  const [erro, setErro] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)
  const [, setTique] = useState(0)

  useEffect(() => aoMudarFimDaSessao(setFim), [])

  // A contagem regressiva do título: renderiza de novo a cada 15 s enquanto o aviso está aberto
  useEffect(() => {
    if (fim?.estado !== 'expirando') return
    const intervalo = window.setInterval(() => setTique((t) => t + 1), 15_000)
    return () => window.clearInterval(intervalo)
  }, [fim])

  useEffect(() => {
    setSenha('')
    setErro(null)
  }, [fim?.estado])

  if (!fim || (fim.estado === 'expirando' && dispensadoEm === fim.fim)) return null

  const expirando = fim.estado === 'expirando'
  const minutos = expirando ? Math.max(1, Math.ceil((fim.fim - Date.now()) / 60_000)) : 0

  async function continuar(evento: FormEvent) {
    evento.preventDefault()
    if (!senha) return
    setEnviando(true)
    setErro(null)
    try {
      await entrar(sessao.usuario.email, senha)
    } catch (e) {
      setErro(e instanceof ErroDeEntrada ? e.message : 'Não foi possível continuar. Tente de novo.')
    } finally {
      setEnviando(false)
    }
  }

  return (
    <Modal
      open
      size="sm"
      // Expirando, Esc e "Agora não" adiam para o fim; encerrada, só a senha ou sair
      onClose={() => expirando && setDispensadoEm(fim.fim)}
      title={expirando ? `Sua sessão termina em ${minutos} ${minutos === 1 ? 'minuto' : 'minutos'}` : 'Sua sessão terminou'}
      footer={
        <>
          <Button variant="ghost" onClick={() => void sair()}>Sair</Button>
          {expirando && <Button variant="outline" onClick={() => setDispensadoEm(fim.fim)}>Agora não</Button>}
          <Button type="submit" form="reentrada" loading={enviando} disabled={!senha}>Continuar</Button>
        </>
      }
    >
      <form id="reentrada" onSubmit={(e) => void continuar(e)} className="flex flex-col gap-4">
        <p className="text-sm text-texto-2">
          {expirando
            ? 'Por segurança, cada acesso dura até oito horas. Digite a senha para continuar de onde parou.'
            : 'Digite a senha para continuar de onde parou. O que está na tela não se perde.'}
        </p>
        {/* Para o gerenciador de senhas saber de qual conta é a senha */}
        <input type="email" name="email" autoComplete="username" value={sessao.usuario.email} readOnly hidden />
        <FormField label={`Senha de ${sessao.usuario.email}`} htmlFor="senha-reentrada" error={erro}>
          <Input
            id="senha-reentrada"
            type="password"
            autoComplete="current-password"
            autoFocus
            value={senha}
            maxLength={72}
            invalid={!!erro}
            onChange={(e) => setSenha(e.target.value)}
          />
        </FormField>
      </form>
    </Modal>
  )
}
