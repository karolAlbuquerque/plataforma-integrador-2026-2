import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { ArrowLeft, Info, Pencil, RotateCw } from 'lucide-react'
import {
  Badge, Button, Checkbox, EmptyState, FilterBar, FormField, FormSection, Input, Modal, PageHeader, SearchBar, Spinner, Textarea,
} from '@/components/ui'
import { ErroDaApi, api, mensagemDe } from '../../plataforma/api'
import { avisar } from '../../plataforma/avisos'
import { useCarregar, useCasca } from '../../plataforma/contexto'
import { normalizar } from '../../plataforma/formato'
import { navegar } from '../../plataforma/rotas'
import type { PerfilDetalhe, PermissaoDoCatalogo, PermissoesDoModulo } from '../../plataforma/tipos'

const ORDEM_DAS_ACOES = ['acessar', 'ver', 'ver_resumo', 'criar', 'editar', 'excluir', 'exportar', 'aprovar', 'administrar', 'compartilhar']

const ROTULO_DA_ACAO: Record<string, string> = {
  acessar: 'Acessar', ver: 'Ver', ver_resumo: 'Ver resumo', criar: 'Criar', editar: 'Editar', excluir: 'Excluir',
  exportar: 'Exportar', aprovar: 'Aprovar', administrar: 'Administrar', compartilhar: 'Compartilhar',
}

/**
 * Os códigos de permissão não têm acento (usuario, cobranca); para exibir, as palavras mais comuns
 * dos recursos dos módulos voltam a ter. Palavra fora da lista aparece como está.
 */
const COM_ACENTO: Record<string, string> = {
  usuario: 'usuário', usuarios: 'usuários', servico: 'serviço', servicos: 'serviços', cobranca: 'cobrança',
  cobrancas: 'cobranças', relatorio: 'relatório', relatorios: 'relatórios', formulario: 'formulário',
  formularios: 'formulários', orcamento: 'orçamento', orcamentos: 'orçamentos', pagina: 'página', paginas: 'páginas',
  negociacao: 'negociação', integracao: 'integração', configuracao: 'configuração', configuracoes: 'configurações',
  automacao: 'automação', automacoes: 'automações', comissao: 'comissão', comissoes: 'comissões',
  conciliacao: 'conciliação', notificacao: 'notificação', notificacoes: 'notificações', historico: 'histórico',
  analise: 'análise', metrica: 'métrica', metricas: 'métricas', tecnico: 'técnico', credito: 'crédito',
  debito: 'débito', periodo: 'período', calendario: 'calendário', catalogo: 'catálogo', preco: 'preço', precos: 'preços', emissao: 'emissão', situacao: 'situação',
}

const capitalizar = (texto: string) => texto.charAt(0).toUpperCase() + texto.slice(1)
const legivel = (codigo: string) => capitalizar(codigo.split('_').map((palavra) => COM_ACENTO[palavra] ?? palavra).join(' '))
const rotuloDaAcao = (acao: string) => ROTULO_DA_ACAO[acao] ?? legivel(acao)
/** {modulo}.acessar tem recurso "modulo": é a presença do módulo no menu. */
const rotuloDoRecurso = (recurso: string) => (recurso === 'modulo' ? 'Menu do módulo' : legivel(recurso))

function ordenarAcoes(acoes: string[]) {
  const posicao = (acao: string) => {
    const indice = ORDEM_DAS_ACOES.indexOf(acao)
    return indice === -1 ? ORDEM_DAS_ACOES.length : indice
  }
  return [...new Set(acoes)].sort((a, b) => posicao(a) - posicao(b) || a.localeCompare(b))
}

/**
 * Matriz de montagem de perfil (RF22): cada módulo numa tabela de recurso por ação, com marcar e
 * desmarcar em bloco por linha, por coluna e pelo módulo inteiro. Tudo vai numa gravação só. Só se
 * concede o que se tem: as caixas de permissões que você não tem ficam travadas.
 */
export function Matriz({ id }: { id: string }) {
  const { eu, tem } = useCasca()
  const perfil = useCarregar(() => api.get<PerfilDetalhe>(`/api/identity/perfis/${id}`), [id])
  const catalogo = useCarregar(() => api.get<PermissoesDoModulo[]>('/api/identity/permissoes'), [])
  const [marcadas, setMarcadas] = useState<Set<string>>(new Set())
  const [filtro, setFiltro] = useState('')
  const [salvando, setSalvando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const [editandoDados, setEditandoDados] = useState(false)

  const dados = perfil.carga.tipo === 'pronto' ? perfil.carga.dados : null
  useEffect(() => {
    if (dados) setMarcadas(new Set(dados.permissoes))
  }, [dados])

  const minhas = useMemo(() => new Set(eu?.permissoes ?? []), [eu])
  const administra = tem('identity.perfil.administrar')
  const perfilDentroDasMinhas = dados ? dados.permissoes.every((p) => minhas.has(p)) : false
  const podeEditar = administra && perfilDentroDasMinhas

  const mudancas = useMemo(() => {
    if (!dados) return 0
    const originais = new Set(dados.permissoes)
    let total = 0
    marcadas.forEach((p) => { if (!originais.has(p)) total++ })
    originais.forEach((p) => { if (!marcadas.has(p)) total++ })
    return total
  }, [marcadas, dados])

  useEffect(() => {
    if (mudancas === 0) return
    const avisarAntesDeSair = (evento: BeforeUnloadEvent) => evento.preventDefault()
    window.addEventListener('beforeunload', avisarAntesDeSair)
    return () => window.removeEventListener('beforeunload', avisarAntesDeSair)
  }, [mudancas])

  /** Em bloco, só entra o que você pode conceder; tirar, pode tudo (se pode editar o perfil). */
  function alternar(codigos: string[], marcar: boolean) {
    setMarcadas((atual) => {
      const novo = new Set(atual)
      for (const codigo of codigos) {
        if (marcar && minhas.has(codigo)) novo.add(codigo)
        if (!marcar) novo.delete(codigo)
      }
      return novo
    })
  }

  async function salvar() {
    if (!dados) return
    setSalvando(true)
    setErro(null)
    try {
      await api.put<PerfilDetalhe>(`/api/identity/perfis/${dados.id}`, { nome: dados.nome, descricao: dados.descricao, permissoes: [...marcadas] })
      avisar('sucesso', 'Permissões salvas. Valem para cada usuário na próxima renovação do token, em até 15 minutos.')
      perfil.recarregar()
    } catch (e) {
      setErro(mensagemDe(e))
    } finally {
      setSalvando(false)
    }
  }

  const modulos = useMemo(() => {
    if (catalogo.carga.tipo !== 'pronto') return []
    const busca = normalizar(filtro.trim())
    if (!busca) return catalogo.carga.dados
    return catalogo.carga.dados
      .map((modulo) => normalizar(modulo.nome).includes(busca)
        ? modulo
        : { ...modulo, permissoes: modulo.permissoes.filter((p) => normalizar(`${p.codigo} ${p.descricao} ${rotuloDoRecurso(p.recurso)} ${rotuloDaAcao(p.acao)}`).includes(busca)) })
      .filter((modulo) => modulo.permissoes.length > 0)
  }, [catalogo.carga, filtro])

  const totalNoCatalogo = catalogo.carga.tipo === 'pronto' ? catalogo.carga.dados.reduce((soma, m) => soma + m.permissoes.length, 0) : 0

  if (perfil.carga.tipo === 'carregando' || catalogo.carga.tipo === 'carregando') return <Spinner />
  if (perfil.carga.tipo === 'erro' || catalogo.carga.tipo === 'erro') {
    const falha = perfil.carga.tipo === 'erro' ? perfil.carga : catalogo.carga.tipo === 'erro' ? catalogo.carga : null
    return (
      <EmptyState
        title={falha?.status === 404 ? 'Perfil não encontrado' : 'Não foi possível abrir o perfil'}
        action={<Button variant="outline" onClick={() => navegar('/admin/perfis')}><ArrowLeft aria-hidden className="size-4" /> Voltar aos perfis</Button>}
      >
        {falha?.status === 404 ? 'Ele foi excluído ou não existe nesta empresa.' : falha?.mensagem}
      </EmptyState>
    )
  }
  if (!dados) return null

  return (
    <div className="flex flex-1 flex-col">
      <PageHeader
        title={dados.rotulo}
        subtitle={`${marcadas.size} de ${totalNoCatalogo} permissões · ${dados.totalUsuarios === 1 ? '1 usuário' : `${dados.totalUsuarios} usuários`} com este perfil`}
        actions={
          <>
            <Button variant="secondary" size="sm" onClick={() => navegar('/admin/perfis')}>
              <ArrowLeft aria-hidden className="size-4" /> Perfis
            </Button>
            {podeEditar && (
              <Button variant="secondary" size="sm" disabled={mudancas > 0} title={mudancas > 0 ? 'Salve ou descarte as permissões antes' : undefined}
                onClick={() => setEditandoDados(true)}>
                <Pencil aria-hidden className="size-3.5" /> Nome e descrição
              </Button>
            )}
          </>
        }
      />
      <FilterBar>
        <SearchBar className="min-w-52 flex-1" placeholder="Filtrar módulos ou permissões…" aria-label="Filtrar módulos ou permissões"
          value={filtro} onChange={(e) => setFiltro(e.target.value)} />
        {dados.sistema && <Badge>perfil de sistema</Badge>}
      </FilterBar>

      <div className="grid flex-1 content-start gap-4 p-5">
        {dados.descricao && <p className="text-sm text-texto-2">{dados.descricao}</p>}
        {!podeEditar && (
          <p className="flex items-start gap-2 rounded-lg bg-blue-50 px-3 py-2 text-sm text-blue-800 dark:bg-blue-500/15 dark:text-blue-200">
            <Info aria-hidden className="mt-0.5 size-4 shrink-0" />
            {administra
              ? 'Este perfil tem permissões que você não tem. Só quem tem todas elas pode alterá-lo.'
              : 'Você pode ver as permissões deste perfil, mas não alterá-las.'}
          </p>
        )}
        {modulos.length === 0 && <EmptyState title="Nenhuma permissão encontrada">Mude o filtro.</EmptyState>}
        {modulos.map((modulo) => (
          <MatrizDoModulo key={modulo.modulo} modulo={modulo} marcadas={marcadas} minhas={minhas} podeEditar={podeEditar} aoAlternar={alternar} />
        ))}
      </div>

      {podeEditar && mudancas > 0 && (
        <div className="sticky bottom-0 z-20 flex flex-wrap items-center justify-between gap-3 border-t border-borda bg-superficie px-5 py-3 shadow-[0_-4px_12px_rgb(0_0_0/0.06)]">
          <p className="text-sm text-texto-2" role="status">
            {mudancas === 1 ? '1 alteração não salva' : `${mudancas} alterações não salvas`}
            {erro && <span className="mt-0.5 block text-red-600 dark:text-red-400" role="alert">{erro}</span>}
          </p>
          <div className="flex gap-2">
            <Button variant="outline" onClick={() => { setMarcadas(new Set(dados.permissoes)); setErro(null) }}>
              <RotateCw aria-hidden className="size-4" /> Descartar
            </Button>
            <Button loading={salvando} onClick={() => void salvar()}>Salvar permissões</Button>
          </div>
        </div>
      )}

      {editandoDados && <EditarDados perfil={dados} aoFechar={() => setEditandoDados(false)} aoSalvar={() => { setEditandoDados(false); perfil.recarregar() }} />}
    </div>
  )
}

function MatrizDoModulo({ modulo, marcadas, minhas, podeEditar, aoAlternar }: {
  modulo: PermissoesDoModulo
  marcadas: Set<string>
  minhas: Set<string>
  podeEditar: boolean
  aoAlternar: (codigos: string[], marcar: boolean) => void
}) {
  const acoes = ordenarAcoes(modulo.permissoes.map((p) => p.acao))
  const recursos = [...new Set(modulo.permissoes.map((p) => p.recurso))]
  const celulas = new Map(modulo.permissoes.map((p) => [`${p.recurso}|${p.acao}`, p]))
  const codigos = modulo.permissoes.map((p) => p.codigo)

  /** Estado de uma caixa em bloco: todas, parte ou nenhuma das que ela controla. */
  function bloco(daqui: PermissaoDoCatalogo[]) {
    const lista = daqui.map((p) => p.codigo)
    const quantas = lista.filter((c) => marcadas.has(c)).length
    return {
      lista,
      todas: quantas === lista.length && lista.length > 0,
      parte: quantas > 0 && quantas < lista.length,
      // Marcar em bloco só faz sentido se houver alguma que você possa conceder
      travada: !podeEditar || (quantas === 0 && !lista.some((c) => minhas.has(c))),
    }
  }

  const doModulo = bloco(modulo.permissoes)
  return (
    <section aria-label={modulo.nome} className="overflow-hidden rounded-xl border border-borda bg-superficie shadow-sm">
      <header className="flex items-center justify-between gap-3 border-b border-borda px-4 py-3">
        <label className="flex items-center gap-2.5">
          <Checkbox checked={doModulo.todas} indeterminate={doModulo.parte} disabled={doModulo.travada}
            aria-label={`Todas as permissões de ${modulo.nome}`} onChange={() => aoAlternar(doModulo.lista, !doModulo.todas)} />
          <span className="font-semibold text-titulo">{modulo.nome}</span>
        </label>
        <span className="text-xs text-texto-3 tabular-nums">
          {codigos.filter((c) => marcadas.has(c)).length} de {codigos.length}
        </span>
      </header>
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="bg-superficie-2 text-xs text-texto-3">
            <tr>
              <th scope="col" className="px-4 py-2 text-left font-semibold tracking-wide uppercase">Recurso</th>
              {acoes.map((acao) => {
                const coluna = bloco(recursos.map((r) => celulas.get(`${r}|${acao}`)).filter((p): p is PermissaoDoCatalogo => !!p))
                return (
                  <th key={acao} scope="col" className="px-2 py-2 font-medium">
                    <label className="flex min-w-16 flex-col items-center gap-1">
                      <Checkbox checked={coluna.todas} indeterminate={coluna.parte} disabled={coluna.travada}
                        aria-label={`${rotuloDaAcao(acao)} em todos os recursos de ${modulo.nome}`}
                        onChange={() => aoAlternar(coluna.lista, !coluna.todas)} />
                      {rotuloDaAcao(acao)}
                    </label>
                  </th>
                )
              })}
            </tr>
          </thead>
          <tbody>
            {recursos.map((recurso) => {
              const linha = bloco(acoes.map((a) => celulas.get(`${recurso}|${a}`)).filter((p): p is PermissaoDoCatalogo => !!p))
              return (
                <tr key={recurso} className="border-t border-borda hover:bg-brand-50/50 dark:hover:bg-superficie-2">
                  <th scope="row" className="px-4 py-2 text-left font-normal">
                    <label className="flex items-center gap-2.5">
                      <Checkbox checked={linha.todas} indeterminate={linha.parte} disabled={linha.travada}
                        aria-label={`Todas as ações de ${rotuloDoRecurso(recurso)}`} onChange={() => aoAlternar(linha.lista, !linha.todas)} />
                      <span className="text-texto">{rotuloDoRecurso(recurso)}</span>
                    </label>
                  </th>
                  {acoes.map((acao) => {
                    const permissao = celulas.get(`${recurso}|${acao}`)
                    if (!permissao) return <td key={acao} aria-hidden className="text-center text-texto-3">·</td>
                    const marcada = marcadas.has(permissao.codigo)
                    const podeConceder = minhas.has(permissao.codigo)
                    return (
                      <td key={acao} className="px-2 py-2 text-center">
                        <Checkbox
                          checked={marcada}
                          disabled={!podeEditar || (!marcada && !podeConceder)}
                          title={`${permissao.codigo} — ${permissao.descricao}${!podeConceder ? ' (você não tem esta permissão)' : ''}`}
                          aria-label={`${rotuloDoRecurso(recurso)}: ${rotuloDaAcao(acao)} — ${permissao.descricao}`}
                          onChange={(e) => aoAlternar([permissao.codigo], e.target.checked)}
                        />
                      </td>
                    )
                  })}
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </section>
  )
}

function EditarDados({ perfil, aoFechar, aoSalvar }: { perfil: PerfilDetalhe; aoFechar: () => void; aoSalvar: () => void }) {
  const [nome, setNome] = useState(perfil.nome)
  const [descricao, setDescricao] = useState(perfil.descricao ?? '')
  const [erros, setErros] = useState<Record<string, string>>({})
  const [salvando, setSalvando] = useState(false)

  async function salvar(evento: FormEvent) {
    evento.preventDefault()
    setSalvando(true)
    setErros({})
    try {
      await api.put<PerfilDetalhe>(`/api/identity/perfis/${perfil.id}`, { nome: nome.trim(), descricao: descricao.trim() || null, permissoes: perfil.permissoes })
      avisar('sucesso', 'Perfil atualizado.')
      aoSalvar()
    } catch (e) {
      if (e instanceof ErroDaApi) setErros(Object.keys(e.porCampo).length ? e.porCampo : { geral: e.message })
      else setErros({ geral: mensagemDe(e) })
    } finally {
      setSalvando(false)
    }
  }

  return (
    <Modal
      open
      onClose={aoFechar}
      size="lg"
      title="Nome e descrição"
      footer={
        <>
          <Button variant="outline" onClick={aoFechar}>Cancelar</Button>
          <Button type="submit" form="dados-do-perfil" loading={salvando} disabled={!nome.trim()}>Salvar</Button>
        </>
      }
    >
      <form id="dados-do-perfil" onSubmit={salvar} noValidate>
        {erros.geral && <p role="alert" className="mb-4 text-sm text-red-600 dark:text-red-400">{erros.geral}</p>}
        <FormSection number={1} title="Identificação">
          <FormField label="Nome" required colSpan={2} htmlFor="dados-nome" error={erros.nome}
            hint={perfil.sistema ? 'Perfil de sistema não muda de nome: o catálogo concede permissões novas a ele pelo nome.' : undefined}>
            <Input id="dados-nome" value={nome} maxLength={60} disabled={perfil.sistema} invalid={!!erros.nome} onChange={(e) => setNome(e.target.value)} />
          </FormField>
          <FormField label="Descrição" colSpan={2} htmlFor="dados-descricao" error={erros.descricao}>
            <Textarea id="dados-descricao" autoFocus value={descricao} maxLength={300} onChange={(e) => setDescricao(e.target.value)} />
          </FormField>
        </FormSection>
      </form>
    </Modal>
  )
}
