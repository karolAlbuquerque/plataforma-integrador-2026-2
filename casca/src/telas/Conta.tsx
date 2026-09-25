import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { Download, KeyRound, Laptop, Monitor, Moon, Sun } from 'lucide-react'
import { Badge, Button, cx, FormField, Input, Modal, PageHeader, Spinner } from '@/components/ui'
import { ErroDaApi, api, mensagemDe } from '../plataforma/api'
import { avisar } from '../plataforma/avisos'
import { useCarregar, useCasca, type Carga } from '../plataforma/contexto'
import { dataHora, haQuanto, senhaAceitavel } from '../plataforma/formato'
import { baixarTexto, CampoDeCodigo, codigoCompleto, ListaDeCodigos, QrCodeDoSegredo } from '../componentes/SegundoFator'
import type { CadastroDeSegundoFator, Conta as DadosDaConta, PreferenciaDeTema, SessaoAtiva, SituacaoDoSegundoFator } from '../plataforma/tipos'
import { RegrasDaSenha } from './publicas/TelaPublica'

/**
 * Minha conta (RF18, RF08, RF06): os próprios dados, a troca de senha e as sessões abertas. Perfis
 * e equipes aparecem só para leitura — quem muda é um administrador. Na onda 4: a verificação em
 * duas etapas (RF10), o tema guardado na conta (RF40) e o download dos próprios dados (RF56).
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
        <DuasEtapas />
        <Aparencia />
        <MeusDados />
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
  const { preferenciaDeTema, definirPreferenciaDeTema } = useCasca()
  const opcoes: { valor: PreferenciaDeTema; rotulo: string; icone: typeof Sun }[] = [
    { valor: 'claro', rotulo: 'Claro', icone: Sun },
    { valor: 'escuro', rotulo: 'Escuro', icone: Moon },
    { valor: 'sistema', rotulo: 'Sistema', icone: Monitor },
  ]
  return (
    <Painel titulo="Aparência" descricao="Fica guardada na sua conta e vale em qualquer navegador, inclusive nos módulos.">
      <div role="radiogroup" aria-label="Tema" className="inline-flex rounded-lg border border-borda p-0.5">
        {opcoes.map((opcao) => (
          <button
            key={opcao.valor}
            type="button"
            role="radio"
            aria-checked={preferenciaDeTema === opcao.valor}
            onClick={() => preferenciaDeTema !== opcao.valor && definirPreferenciaDeTema(opcao.valor)}
            className={cx(
              'flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-medium transition-colors',
              preferenciaDeTema === opcao.valor ? 'bg-brand-950 text-white' : 'text-texto-2 hover:bg-superficie-2',
            )}
          >
            <opcao.icone aria-hidden className="size-4" /> {opcao.rotulo}
          </button>
        ))}
      </div>
    </Painel>
  )
}

/** LGPD art. 18, II: o que a plataforma guarda de você, num arquivo JSON. */
function MeusDados() {
  const { sessao } = useCasca()
  const [baixando, setBaixando] = useState(false)

  async function baixar() {
    setBaixando(true)
    try {
      const dados = await api.get<unknown>('/api/identity/conta/dados-pessoais')
      baixarTexto(`meus-dados-${sessao.usuario.id}.json`, JSON.stringify(dados, null, 2), 'application/json')
    } catch (e) {
      avisar('erro', mensagemDe(e))
    } finally {
      setBaixando(false)
    }
  }

  return (
    <Painel titulo="Meus dados" descricao="Cadastro, perfis, sessões, tentativas de entrada, notificações e o que você fez na plataforma (LGPD).">
      <Button variant="outline" loading={baixando} onClick={() => void baixar()}>
        <Download aria-hidden className="size-4" /> Baixar meus dados
      </Button>
    </Painel>
  )
}

type PassoDaTroca =
  | { tipo: 'identidade'; intencao: 'troca' | 'codigos' }
  | { tipo: 'qr'; cadastro: CadastroDeSegundoFator }
  | { tipo: 'codigos'; codigos: string[] }

/**
 * RF10: trocar de aparelho e gerar códigos novos. Não existe "desativar" (decisão de 25/09). Com o
 * segundo fator não obrigatório (só no desenvolvimento), daqui também se ativa.
 */
function DuasEtapas() {
  const { sessao } = useCasca()
  const { carga, recarregar } = useCarregar(() => api.get<SituacaoDoSegundoFator>('/api/identity/conta/segundo-fator'), [])
  const [passo, setPasso] = useState<PassoDaTroca | null>(null)
  const [senha, setSenha] = useState('')
  const [codigo, setCodigo] = useState('')
  const [erro, setErro] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)
  const situacao = carga.tipo === 'pronto' ? carga.dados : null

  function abrir(intencao: 'troca' | 'codigos') {
    setSenha('')
    setCodigo('')
    setErro(null)
    setPasso({ tipo: 'identidade', intencao })
  }

  function fechar() {
    if (passo?.tipo === 'codigos') recarregar()
    setPasso(null)
  }

  async function confirmar(evento: FormEvent) {
    evento.preventDefault()
    if (!passo || passo.tipo === 'codigos') return
    setEnviando(true)
    setErro(null)
    try {
      if (passo.tipo === 'identidade') {
        const corpo = { senha, codigo: situacao?.ativo ? codigo.trim() : undefined }
        if (passo.intencao === 'troca') {
          setPasso({ tipo: 'qr', cadastro: await api.post<CadastroDeSegundoFator>('/api/identity/conta/segundo-fator/troca', corpo) })
        } else {
          const { codigosRecuperacao } = await api.post<{ codigosRecuperacao: string[] }>('/api/identity/conta/segundo-fator/codigos', corpo)
          setPasso({ tipo: 'codigos', codigos: codigosRecuperacao })
        }
      } else {
        const { codigosRecuperacao } = await api.post<{ codigosRecuperacao: string[] }>(
          '/api/identity/conta/segundo-fator/troca/confirmar', { codigo: codigo.trim() })
        setPasso({ tipo: 'codigos', codigos: codigosRecuperacao })
        avisar('sucesso', 'Aplicativo autenticador cadastrado. O anterior deixou de valer.')
      }
      setCodigo('')
    } catch (e) {
      setErro(mensagemDe(e))
      setCodigo('')
    } finally {
      setEnviando(false)
    }
  }

  const pronto = passo?.tipo === 'qr'
    ? codigoCompleto(codigo)
    : !!senha && (!situacao?.ativo || codigoCompleto(codigo))
  const titulo = passo?.tipo === 'codigos'
    ? 'Guarde os códigos de recuperação'
    : passo?.tipo === 'qr' || (passo?.tipo === 'identidade' && passo.intencao === 'troca')
      ? situacao?.ativo ? 'Trocar de aparelho' : 'Ativar a verificação em duas etapas'
      : 'Gerar novos códigos de recuperação'

  return (
    <Painel titulo="Verificação em duas etapas" descricao="Depois da senha, a plataforma pede o código do aplicativo autenticador do seu celular.">
      {carga.tipo === 'carregando' && <Spinner />}
      {carga.tipo === 'erro' && <p role="alert" className="text-sm text-red-600">{carga.mensagem}</p>}
      {situacao && (
        <>
          <p className="flex flex-wrap items-center gap-2 text-sm text-texto">
            {situacao.ativo ? <Badge variant="green">Ativa</Badge> : <Badge variant="yellow">Não cadastrada</Badge>}
            {situacao.ativo && (
              <span className="text-texto-3">
                desde {dataHora(situacao.ativadoEm)} · {situacao.codigosRestantes}{' '}
                {situacao.codigosRestantes === 1 ? 'código de recuperação restante' : 'códigos de recuperação restantes'}
              </span>
            )}
          </p>
          <div className="mt-4 flex flex-wrap gap-2">
            <Button variant="outline" size="sm" onClick={() => abrir('troca')}>
              <KeyRound aria-hidden className="size-4" /> {situacao.ativo ? 'Trocar de aparelho' : 'Ativar'}
            </Button>
            {situacao.ativo && (
              <Button variant="ghost" size="sm" onClick={() => abrir('codigos')}>Gerar novos códigos</Button>
            )}
          </div>
        </>
      )}

      <Modal
        open={passo !== null}
        onClose={fechar}
        size="sm"
        title={titulo}
        footer={passo?.tipo === 'codigos'
          ? <Button onClick={fechar}>Guardei os códigos</Button>
          : (
            <>
              <Button variant="outline" onClick={fechar}>Cancelar</Button>
              <Button type="submit" form="duas-etapas" loading={enviando} disabled={!pronto}>
                {passo?.tipo === 'qr' ? 'Confirmar' : 'Continuar'}
              </Button>
            </>
          )}
      >
        {passo?.tipo === 'codigos' ? (
          <div className="flex flex-col gap-3">
            <p className="text-sm text-texto-2">Cada código vale uma vez. Os anteriores deixaram de valer e estes não aparecem de novo.</p>
            <ListaDeCodigos codigos={passo.codigos} email={sessao.usuario.email} />
          </div>
        ) : (
          <form id="duas-etapas" onSubmit={(e) => void confirmar(e)} className="flex flex-col gap-4">
            {passo?.tipo === 'qr' ? (
              <>
                <p className="text-sm text-texto-2">Leia o QR Code com o aplicativo do aparelho novo e digite o código que aparecer.</p>
                <QrCodeDoSegredo cadastro={passo.cadastro} />
                <FormField label="Código do aparelho novo" htmlFor="duas-etapas-novo" error={erro}>
                  <CampoDeCodigo id="duas-etapas-novo" valor={codigo} aoMudar={setCodigo} invalido={!!erro} />
                </FormField>
              </>
            ) : (
              <>
                <p className="text-sm text-texto-2">Confirme que é você.</p>
                <input type="email" autoComplete="username" value={sessao.usuario.email} hidden readOnly />
                <FormField label="Senha" htmlFor="duas-etapas-senha" error={situacao?.ativo ? null : erro}>
                  <Input id="duas-etapas-senha" type="password" autoComplete="current-password" autoFocus value={senha}
                    onChange={(e) => setSenha(e.target.value)} />
                </FormField>
                {situacao?.ativo && (
                  <FormField label="Código do aplicativo atual" htmlFor="duas-etapas-codigo" error={erro}>
                    <CampoDeCodigo id="duas-etapas-codigo" valor={codigo} aoMudar={setCodigo} invalido={!!erro} focar={false} />
                  </FormField>
                )}
              </>
            )}
          </form>
        )}
      </Modal>
    </Painel>
  )
}
