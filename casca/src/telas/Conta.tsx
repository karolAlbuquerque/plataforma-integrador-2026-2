import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { Laptop, Moon, Sun } from 'lucide-react'
import { Badge, Button, cx, FormField, Input, Modal, PageHeader, Spinner } from '@/components/ui'
import { ErroDaApi, api, mensagemDe } from '../plataforma/api'
import { avisar } from '../plataforma/avisos'
import { useCarregar, useCasca, type Carga } from '../plataforma/contexto'
import { dataHora, haQuanto, senhaAceitavel } from '../plataforma/formato'
import type { Conta as DadosDaConta, SessaoAtiva } from '../plataforma/tipos'
import { RegrasDaSenha } from './publicas/TelaPublica'

/**
 * Minha conta (RF18, RF08, RF06): os próprios dados, a troca de senha e as sessões abertas. Perfis
 * e equipes aparecem só para leitura — quem muda é um administrador.
 */
export function Conta() {
  const { carga, recarregar } = useCarregar(() => api.get<DadosDaConta>('/api/identity/conta'), [])
  const sessoes = useCarregar(() => api.get<SessaoAtiva[]>('/api/identity/auth/sessoes'), [])

  return (
    <div className="flex flex-1 flex-col">
      <PageHeader title="Minha conta" subtitle={carga.tipo === 'pronto' ? carga.dados.email : undefined} />
      <div className="mx-auto grid w-full max-w-5xl gap-5 p-5 lg:grid-cols-2">
        {carga.tipo === 'carregando' && <Spinner className="lg:col-span-2" />}
        {carga.tipo === 'erro' && <p role="alert" className="text-sm text-red-600 lg:col-span-2">{carga.mensagem}</p>}
        {carga.tipo === 'pronto' && <DadosPessoais conta={carga.dados} aoSalvar={recarregar} />}
        <TrocaDeSenha aoTrocar={sessoes.recarregar} />
        <Sessoes carga={sessoes.carga} recarregar={sessoes.recarregar} />
        <Aparencia />
      </div>
    </div>
  )
}

function Painel({ titulo, descricao, children, className }: { titulo: string; descricao?: string; children: ReactNode; className?: string }) {
  return (
    <section aria-label={titulo} className={cx('rounded-xl border border-borda bg-superficie p-5 shadow-sm', className)}>
      <h2 className="font-semibold text-titulo">{titulo}</h2>
      {descricao && <p className="mt-0.5 text-sm text-texto-3">{descricao}</p>}
      <div className="mt-4">{children}</div>
    </section>
  )
}

function DadosPessoais({ conta, aoSalvar }: { conta: DadosDaConta; aoSalvar: () => void }) {
  const [nome, setNome] = useState(conta.nome)
  const [telefone, setTelefone] = useState(conta.telefone ?? '')
  const [erros, setErros] = useState<Record<string, string>>({})
  const [salvando, setSalvando] = useState(false)
  const mudou = nome.trim() !== conta.nome || (telefone.trim() || null) !== conta.telefone

  useEffect(() => {
    setNome(conta.nome)
    setTelefone(conta.telefone ?? '')
  }, [conta])

  async function salvar(evento: FormEvent) {
    evento.preventDefault()
    setSalvando(true)
    setErros({})
    try {
      // Telefone vazio vai como null: o contrato não aceita texto vazio
      await api.put('/api/identity/conta', { nome: nome.trim(), telefone: telefone.trim() || null })
      avisar('sucesso', 'Dados salvos.')
      aoSalvar()
    } catch (e) {
      if (e instanceof ErroDaApi && e.status === 400) setErros(e.porCampo)
      else avisar('erro', mensagemDe(e))
    } finally {
      setSalvando(false)
    }
  }

  return (
    <Painel titulo="Dados pessoais" descricao="O e-mail, os perfis e as equipes são definidos pelo administrador.">
      <form onSubmit={salvar} noValidate className="grid gap-3.5">
        <FormField label="Nome" required htmlFor="conta-nome" error={erros.nome}>
          <Input id="conta-nome" value={nome} maxLength={120} invalid={!!erros.nome} onChange={(e) => setNome(e.target.value)} />
        </FormField>
        <FormField label="Telefone" htmlFor="conta-telefone" error={erros.telefone} hint="Com DDD, por exemplo (62) 99999-0000.">
          <Input id="conta-telefone" type="tel" value={telefone} maxLength={20} invalid={!!erros.telefone} onChange={(e) => setTelefone(e.target.value)} />
        </FormField>
        <FormField label="E-mail" htmlFor="conta-email">
          <Input id="conta-email" value={conta.email} disabled />
        </FormField>
        <div>
          <Button type="submit" loading={salvando} disabled={!mudou || !nome.trim()}>Salvar</Button>
        </div>
      </form>

      <dl className="mt-6 grid gap-3 border-t border-borda pt-4 text-sm">
        <div>
          <dt className="text-xs text-texto-3">Empresa</dt>
          <dd className="mt-0.5 text-texto">{conta.tenant.nome ?? '—'}</dd>
        </div>
        <div>
          <dt className="text-xs text-texto-3">Perfis</dt>
          <dd className="mt-1 flex flex-wrap gap-1.5">
            {conta.perfis.length ? conta.perfis.map((p) => <Badge key={p.id} variant="indigo">{p.rotulo}</Badge>) : <span className="text-texto-3">Nenhum</span>}
          </dd>
        </div>
        <div>
          <dt className="text-xs text-texto-3">Equipes</dt>
          <dd className="mt-1 flex flex-wrap gap-1.5">
            {conta.equipes.length
              ? conta.equipes.map((e) => <Badge key={e.id} variant={e.lider ? 'green' : 'gray'}>{e.nome}{e.lider && ' · líder'}</Badge>)
              : <span className="text-texto-3">Nenhuma</span>}
          </dd>
        </div>
        <div>
          <dt className="text-xs text-texto-3">Último acesso</dt>
          <dd className="mt-0.5 text-texto">{dataHora(conta.ultimoLoginEm) ?? '—'}</dd>
        </div>
      </dl>
    </Painel>
  )
}

function TrocaDeSenha({ aoTrocar }: { aoTrocar: () => void }) {
  const { sessao } = useCasca()
  const [atual, setAtual] = useState('')
  const [nova, setNova] = useState('')
  const [confirmacao, setConfirmacao] = useState('')
  const [erros, setErros] = useState<Record<string, string>>({})
  const [enviando, setEnviando] = useState(false)
  const diferentes = confirmacao.length > 0 && confirmacao !== nova

  async function trocar(evento: FormEvent) {
    evento.preventDefault()
    setEnviando(true)
    setErros({})
    try {
      const mensagem = await api.acao('POST', '/api/identity/auth/senha', { senhaAtual: atual, novaSenha: nova })
      avisar('sucesso', mensagem ?? 'Senha trocada.')
      setAtual('')
      setNova('')
      setConfirmacao('')
      aoTrocar()
    } catch (e) {
      if (e instanceof ErroDaApi && (e.status === 400 || e.status === 422)) setErros({ ...e.porCampo, geral: e.message })
      else avisar('erro', mensagemDe(e))
    } finally {
      setEnviando(false)
    }
  }

  return (
    <Painel titulo="Senha" descricao="Trocar a senha encerra as sessões abertas nos outros dispositivos.">
      <form onSubmit={trocar} noValidate className="grid gap-3.5">
        <input type="email" autoComplete="username" value={sessao.usuario.email} hidden readOnly />
        <FormField label="Senha atual" htmlFor="senha-atual" error={erros.senhaAtual}>
          <Input id="senha-atual" type="password" autoComplete="current-password" value={atual} invalid={!!erros.senhaAtual} onChange={(e) => setAtual(e.target.value)} />
        </FormField>
        <FormField label="Senha nova" htmlFor="senha-nova" error={erros.novaSenha}>
          <Input id="senha-nova" type="password" autoComplete="new-password" value={nova} invalid={!!erros.novaSenha} onChange={(e) => setNova(e.target.value)} />
        </FormField>
        <RegrasDaSenha senha={nova} />
        <FormField label="Repita a senha nova" htmlFor="senha-confirmacao" error={diferentes ? 'As duas senhas não são iguais.' : null}>
          <Input id="senha-confirmacao" type="password" autoComplete="new-password" value={confirmacao} invalid={diferentes} onChange={(e) => setConfirmacao(e.target.value)} />
        </FormField>
        {erros.geral && !erros.senhaAtual && !erros.novaSenha && <p role="alert" className="text-sm text-red-600 dark:text-red-400">{erros.geral}</p>}
        <div>
          <Button type="submit" loading={enviando} disabled={!atual || !senhaAceitavel(nova) || nova !== confirmacao}>
            Trocar a senha
          </Button>
        </div>
      </form>
    </Painel>
  )
}

function Sessoes({ carga, recarregar }: { carga: Carga<SessaoAtiva[]>; recarregar: () => void }) {
  const [confirmandoOutras, setConfirmandoOutras] = useState(false)
  const [ocupado, setOcupado] = useState<string | null>(null)
  const sessoes = carga.tipo === 'pronto' ? carga.dados : []
  const outras = sessoes.filter((s) => !s.atual).length

  async function encerrar(id: string | null) {
    setOcupado(id ?? 'outras')
    try {
      const mensagem = await api.acao('DELETE', id ? `/api/identity/auth/sessoes/${id}` : '/api/identity/auth/sessoes')
      avisar('sucesso', mensagem ?? 'Sessão encerrada.')
      setConfirmandoOutras(false)
      recarregar()
    } catch (e) {
      avisar('erro', mensagemDe(e))
    } finally {
      setOcupado(null)
    }
  }

  return (
    <Painel titulo="Sessões ativas" descricao="Onde a sua conta está aberta. Encerrar impede a próxima renovação daquele dispositivo.">
      {carga.tipo === 'carregando' && <Spinner />}
      {carga.tipo === 'erro' && <p role="alert" className="text-sm text-red-600">{carga.mensagem}</p>}
      {carga.tipo === 'pronto' && (
        <>
          <ul className="divide-y divide-borda">
            {sessoes.map((sessao) => (
              <li key={sessao.id} className="flex items-start gap-3 py-3 first:pt-0">
                <Laptop aria-hidden className="mt-0.5 size-5 shrink-0 text-texto-3" />
                <div className="min-w-0 flex-1">
                  <p className="flex flex-wrap items-center gap-2 text-sm font-medium text-texto">
                    {sessao.navegador ?? 'Navegador desconhecido'}
                    {sessao.atual && <Badge variant="green">este dispositivo</Badge>}
                  </p>
                  <p className="text-xs text-texto-3">
                    {sessao.ip ?? 'IP desconhecido'} · entrou em {dataHora(sessao.iniciadaEm)} · ativo {haQuanto(sessao.ultimaRenovacaoEm)}
                  </p>
                </div>
                {!sessao.atual && (
                  <Button variant="ghost" size="sm" loading={ocupado === sessao.id} onClick={() => void encerrar(sessao.id)}>
                    Encerrar
                  </Button>
                )}
              </li>
            ))}
          </ul>
          {outras > 0 && (
            <Button variant="outline" size="sm" className="mt-3" onClick={() => setConfirmandoOutras(true)}>
              Encerrar as outras sessões
            </Button>
          )}
        </>
      )}

      <Modal
        open={confirmandoOutras}
        onClose={() => setConfirmandoOutras(false)}
        size="sm"
        title="Encerrar as outras sessões?"
        footer={
          <>
            <Button variant="outline" onClick={() => setConfirmandoOutras(false)}>Cancelar</Button>
            <Button variant="danger" loading={ocupado === 'outras'} onClick={() => void encerrar(null)}>Encerrar {outras}</Button>
          </>
        }
      >
        <p className="text-sm text-texto-2">
          {outras === 1 ? 'O outro dispositivo' : `Os outros ${outras} dispositivos`} vão precisar entrar de novo em até 15 minutos.
          Esta sessão continua aberta.
        </p>
      </Modal>
    </Painel>
  )
}

function Aparencia() {
  const { tema, alternarTema } = useCasca()
  const opcoes = [
    { valor: 'claro', rotulo: 'Claro', icone: Sun },
    { valor: 'escuro', rotulo: 'Escuro', icone: Moon },
  ] as const
  return (
    <Painel titulo="Aparência" descricao="Vale para este navegador e para os módulos abertos nele.">
      <div role="radiogroup" aria-label="Tema" className="inline-flex rounded-lg border border-borda p-0.5">
        {opcoes.map((opcao) => (
          <button
            key={opcao.valor}
            type="button"
            role="radio"
            aria-checked={tema === opcao.valor}
            onClick={() => tema !== opcao.valor && alternarTema()}
            className={cx(
              'flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-medium transition-colors',
              tema === opcao.valor ? 'bg-brand-950 text-white' : 'text-texto-2 hover:bg-superficie-2',
            )}
          >
            <opcao.icone aria-hidden className="size-4" /> {opcao.rotulo}
          </button>
        ))}
      </div>
    </Painel>
  )
}
