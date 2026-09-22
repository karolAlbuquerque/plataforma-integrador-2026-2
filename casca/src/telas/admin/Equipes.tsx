import { useState, type FormEvent } from 'react'
import { Crown, Pencil, Plus, RotateCw, Trash2, UserPlus, UsersRound, X } from 'lucide-react'
import {
  Avatar, Badge, Button, Checkbox, EmptyState, FilterBar, FormField, FormSection, Input, Modal, PageHeader, Pagination,
  SearchBar, Spinner, statusBadge,
} from '@/components/ui'
import { ErroDaApi, api, consulta, mensagemDe } from '../../plataforma/api'
import { avisar } from '../../plataforma/avisos'
import { useCarregar, useCasca } from '../../plataforma/contexto'
import { SITUACOES, useAtraso } from '../../plataforma/formato'
import type { EquipeDetalhe, EquipeResumo, MembroDaEquipe, Pagina, UsuarioDaLista } from '../../plataforma/tipos'

const TAMANHO = 24

/**
 * Equipes da empresa (RF57, decisão D16). Os módulos usam para escolher responsável e para "o
 * gestor vê o que a equipe vê": o token leva as equipes do usuário, relidas a cada renovação.
 */
export function Equipes() {
  const { tem } = useCasca()
  const administra = tem('identity.equipe.administrar')
  const [busca, setBusca] = useState('')
  const [pagina, setPagina] = useState(0)
  const buscaAtrasada = useAtraso(busca.trim())
  const [aberta, setAberta] = useState<string | null>(null)
  const [formulario, setFormulario] = useState<{ equipe?: EquipeDetalhe } | null>(null)

  const lista = useCarregar(
    () => api.get<Pagina<EquipeResumo>>(`/api/identity/equipes${consulta({ busca: buscaAtrasada, pagina, tamanho: TAMANHO })}`),
    [buscaAtrasada, pagina],
  )
  const total = lista.carga.tipo === 'pronto' ? lista.carga.dados.total : null

  function aoSalvar(equipe: EquipeDetalhe, nova: boolean) {
    setFormulario(null)
    avisar('sucesso', nova ? `Equipe ${equipe.nome} criada.` : 'Equipe atualizada. Os membros veem a mudança na próxima renovação do token.')
    lista.recarregar()
    setAberta(equipe.id)
  }

  return (
    <div className="flex flex-1 flex-col">
      <PageHeader
        title="Equipes"
        subtitle={total === null ? 'Carregando…' : total === 1 ? '1 equipe' : `${total} equipes`}
        actions={administra && (
          <Button size="sm" onClick={() => setFormulario({})}>
            <Plus aria-hidden className="size-4" /> Nova equipe
          </Button>
        )}
      />
      <FilterBar>
        <SearchBar className="min-w-52 flex-1" placeholder="Buscar equipe…" aria-label="Buscar equipe" value={busca} maxLength={100}
          onChange={(e) => { setBusca(e.target.value); setPagina(0) }} />
      </FilterBar>

      <div className="flex-1 p-5">
        {lista.carga.tipo === 'carregando' && <Spinner />}
        {lista.carga.tipo === 'erro' && (
          <EmptyState title="Não foi possível carregar as equipes" action={<Button variant="outline" onClick={lista.recarregar}><RotateCw aria-hidden className="size-4" /> Tentar de novo</Button>}>
            {lista.carga.mensagem}
          </EmptyState>
        )}
        {lista.carga.tipo === 'pronto' && lista.carga.dados.itens.length === 0 && (
          <EmptyState icon={<UsersRound aria-hidden />} title="Nenhuma equipe encontrada">
            {buscaAtrasada ? 'Mude a busca.' : 'Crie equipes para agrupar vendedores, técnicos ou qualquer time que divida a carteira.'}
          </EmptyState>
        )}
        {lista.carga.tipo === 'pronto' && lista.carga.dados.itens.length > 0 && (
          <>
            <ul className={`grid gap-3 sm:grid-cols-2 xl:grid-cols-3 ${lista.atualizando ? 'opacity-60' : ''}`}>
              {lista.carga.dados.itens.map((equipe) => (
                <li key={equipe.id}>
                  <button type="button" onClick={() => setAberta(equipe.id)}
                    className="flex h-full w-full items-start gap-3 rounded-xl border border-borda bg-superficie p-4 text-left shadow-sm transition-colors hover:border-brand-700/40 hover:bg-brand-50 dark:hover:bg-superficie-2">
                    <span className="grid size-10 shrink-0 place-items-center rounded-lg bg-brand-950 text-brand-400">
                      <UsersRound aria-hidden className="size-5" />
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block truncate font-semibold text-titulo">{equipe.nome}</span>
                      <span className="block text-sm text-texto-3">{equipe.totalMembros === 1 ? '1 membro' : `${equipe.totalMembros} membros`}</span>
                      {equipe.lideres.length > 0 && (
                        <span className="mt-2 flex flex-wrap gap-1">
                          {equipe.lideres.map((lider) => <Badge key={lider} variant="green"><Crown aria-hidden className="size-3" /> {lider}</Badge>)}
                        </span>
                      )}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
            <Pagination pagina={pagina} tamanho={TAMANHO} total={lista.carga.dados.total} onChange={setPagina} />
          </>
        )}
      </div>

      <Modal open={aberta !== null} onClose={() => setAberta(null)} size="lg" noPad title="Detalhe da equipe">
        {aberta && (
          <PainelDaEquipe id={aberta} administra={administra} aoFechar={() => setAberta(null)}
            aoEditar={(equipe) => setFormulario({ equipe })}
            aoExcluir={() => { setAberta(null); lista.recarregar() }} />
        )}
      </Modal>

      {formulario && (
        <FormularioDeEquipe equipe={formulario.equipe} aoFechar={() => setFormulario(null)} aoSalvar={(e) => aoSalvar(e, !formulario.equipe)} />
      )}
    </div>
  )
}

function PainelDaEquipe({ id, administra, aoFechar, aoEditar, aoExcluir }: {
  id: string
  administra: boolean
  aoFechar: () => void
  aoEditar: (equipe: EquipeDetalhe) => void
  aoExcluir: () => void
}) {
  const { carga } = useCarregar(() => api.get<EquipeDetalhe>(`/api/identity/equipes/${id}`), [id])
  const [confirmando, setConfirmando] = useState(false)
  const [excluindo, setExcluindo] = useState(false)

  if (carga.tipo !== 'pronto') {
    return <div className="p-6">{carga.tipo === 'carregando' ? <Spinner /> : <p role="alert" className="text-sm text-red-600">{carga.mensagem}</p>}</div>
  }
  const equipe = carga.dados

  async function excluir() {
    setExcluindo(true)
    try {
      const mensagem = await api.acao('DELETE', `/api/identity/equipes/${equipe.id}`)
      avisar('sucesso', mensagem ?? 'Equipe excluída.')
      aoExcluir()
    } catch (e) {
      avisar('erro', mensagemDe(e))
    } finally {
      setExcluindo(false)
    }
  }

  return (
    <div className="flex min-h-0 flex-1 flex-col overflow-hidden">
      <div className="flex shrink-0 items-start justify-between gap-3 bg-brand-950 px-5 pt-5 pb-4 text-white">
        <div className="min-w-0">
          <h2 className="truncate text-lg font-bold">{equipe.nome}</h2>
          <p className="text-sm text-brand-300">{equipe.membros.length === 1 ? '1 membro' : `${equipe.membros.length} membros`}</p>
        </div>
        <div className="flex shrink-0 gap-1.5">
          {administra && (
            <Button variant="secondary" size="sm" onClick={() => aoEditar(equipe)}>
              <Pencil aria-hidden className="size-3" /> Editar
            </Button>
          )}
          <button type="button" onClick={aoFechar} aria-label="Fechar" className="rounded-lg p-1.5 text-brand-300 hover:bg-brand-800 hover:text-white">
            <X aria-hidden className="size-4" />
          </button>
        </div>
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto p-5">
        {equipe.membros.length === 0 ? (
          <p className="text-sm text-texto-3">A equipe ainda não tem membros.</p>
        ) : (
          <ul className="divide-y divide-borda">
            {equipe.membros.map((membro) => (
              <li key={membro.id} className="flex items-center gap-3 py-2.5">
                <Avatar nome={membro.nome} />
                <span className="min-w-0 flex-1 truncate text-sm text-texto">{membro.nome}</span>
                {membro.lider && <Badge variant="green"><Crown aria-hidden className="size-3" /> líder</Badge>}
              </li>
            ))}
          </ul>
        )}
        <p className="mt-4 text-xs text-texto-3">O líder enxerga o que a equipe inteira enxerga nos módulos que usam equipes.</p>
      </div>
      {administra && (
        <div className="flex shrink-0 justify-end border-t border-borda bg-superficie-2 px-5 py-3">
          <Button variant="danger" size="sm" onClick={() => setConfirmando(true)}>
            <Trash2 aria-hidden className="size-3.5" /> Excluir
          </Button>
        </div>
      )}
      <Modal
        open={confirmando}
        onClose={() => setConfirmando(false)}
        size="sm"
        title={`Excluir ${equipe.nome}?`}
        footer={
          <>
            <Button variant="outline" onClick={() => setConfirmando(false)}>Cancelar</Button>
            <Button variant="danger" loading={excluindo} onClick={() => void excluir()}>Excluir</Button>
          </>
        }
      >
        <p className="text-sm text-texto-2">
          A equipe sai do token dos membros na próxima renovação. Registros dos módulos que apontam para ela continuam com o
          identificador, mas ninguém mais a enxerga como equipe.
        </p>
      </Modal>
    </div>
  )
}

function FormularioDeEquipe({ equipe, aoFechar, aoSalvar }: { equipe?: EquipeDetalhe; aoFechar: () => void; aoSalvar: (equipe: EquipeDetalhe) => void }) {
  const { tem } = useCasca()
  const podeBuscarUsuarios = tem('identity.usuario.ver')
  const [nome, setNome] = useState(equipe?.nome ?? '')
  const [membros, setMembros] = useState<MembroDaEquipe[]>(equipe?.membros ?? [])
  const [busca, setBusca] = useState('')
  const buscaAtrasada = useAtraso(busca.trim())
  const [erros, setErros] = useState<Record<string, string>>({})
  const [salvando, setSalvando] = useState(false)

  const encontrados = useCarregar(
    () => (podeBuscarUsuarios && buscaAtrasada
      ? api.get<Pagina<UsuarioDaLista>>(`/api/identity/usuarios${consulta({ busca: buscaAtrasada, tamanho: 8 })}`).then((p) => p.itens)
      : Promise.resolve<UsuarioDaLista[]>([])),
    [buscaAtrasada],
  )
  const candidatos = encontrados.carga.tipo === 'pronto'
    ? encontrados.carga.dados.filter((u) => u.situacao !== 'inativo' && !membros.some((m) => m.id === u.id))
    : []

  async function salvar(evento: FormEvent) {
    evento.preventDefault()
    setSalvando(true)
    setErros({})
    const corpo = { nome: nome.trim(), membros: membros.map((m) => ({ usuarioId: m.id, lider: m.lider })) }
    try {
      const salva = equipe
        ? await api.put<EquipeDetalhe>(`/api/identity/equipes/${equipe.id}`, corpo)
        : await api.post<EquipeDetalhe>('/api/identity/equipes', corpo)
      aoSalvar(salva)
    } catch (e) {
      if (e instanceof ErroDaApi && Object.keys(e.porCampo).length) setErros(e.porCampo)
      else setErros({ geral: mensagemDe(e) })
    } finally {
      setSalvando(false)
    }
  }

  return (
    <Modal
      open
      onClose={aoFechar}
      size="xl"
      title={equipe ? `Editar ${equipe.nome}` : 'Nova equipe'}
      footer={
        <>
          <Button variant="outline" onClick={aoFechar}>Cancelar</Button>
          <Button type="submit" form="formulario-equipe" loading={salvando} disabled={!nome.trim()}>{equipe ? 'Salvar' : 'Criar equipe'}</Button>
        </>
      }
    >
      <form id="formulario-equipe" onSubmit={salvar} noValidate>
        {erros.geral && <p role="alert" className="mb-4 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-500/15 dark:text-red-300">{erros.geral}</p>}
        <FormSection number={1} title="Identificação">
          <FormField label="Nome" required colSpan={2} htmlFor="equipe-nome" error={erros.nome}>
            <Input id="equipe-nome" autoFocus value={nome} maxLength={80} invalid={!!erros.nome} onChange={(e) => setNome(e.target.value)} />
          </FormField>
        </FormSection>

        <FormSection number={2} title="Membros">
          {podeBuscarUsuarios ? (
            <FormField label="Adicionar membro" colSpan={2} htmlFor="equipe-busca" hint="Busque por nome ou e-mail.">
              <SearchBar id="equipe-busca" value={busca} maxLength={100} placeholder="Nome ou e-mail…" onChange={(e) => setBusca(e.target.value)} />
            </FormField>
          ) : (
            <p className="text-sm text-texto-3 md:col-span-2">Para adicionar membros você precisa também da permissão de ver usuários.</p>
          )}

          {candidatos.length > 0 && (
            <ul className="divide-y divide-borda rounded-lg border border-borda md:col-span-2" aria-label="Usuários encontrados">
              {candidatos.map((usuario) => (
                <li key={usuario.id} className="flex items-center gap-3 px-3 py-2">
                  <Avatar nome={usuario.nome} size="sm" />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm text-texto">{usuario.nome}</span>
                    <span className="block truncate text-xs text-texto-3">{usuario.email}</span>
                  </span>
                  {usuario.situacao !== 'ativo' && statusBadge(usuario.situacao, SITUACOES[usuario.situacao])}
                  <Button variant="secondary" size="sm" onClick={() => setMembros((m) => [...m, { id: usuario.id, nome: usuario.nome, lider: false }])}>
                    <UserPlus aria-hidden className="size-3.5" /> Adicionar
                  </Button>
                </li>
              ))}
            </ul>
          )}

          <div className="md:col-span-2">
            <p className={`mb-2 text-xs ${erros.membros ? 'text-red-600 dark:text-red-400' : 'text-texto-3'}`}>
              {erros.membros ?? (membros.length === 0 ? 'Nenhum membro ainda.' : 'Marque quem lidera a equipe.')}
            </p>
            {membros.length > 0 && (
              <ul className="divide-y divide-borda rounded-lg border border-borda" aria-label="Membros da equipe">
                {membros.map((membro) => (
                  <li key={membro.id} className="flex items-center gap-3 px-3 py-2">
                    <Avatar nome={membro.nome} size="sm" />
                    <span className="min-w-0 flex-1 truncate text-sm text-texto">{membro.nome}</span>
                    <label className="flex items-center gap-1.5 text-xs text-texto-2">
                      <Checkbox checked={membro.lider}
                        onChange={(e) => setMembros((lista) => lista.map((m) => (m.id === membro.id ? { ...m, lider: e.target.checked } : m)))} />
                      Líder
                    </label>
                    <Button variant="ghost" size="sm" aria-label={`Tirar ${membro.nome} da equipe`}
                      onClick={() => setMembros((lista) => lista.filter((m) => m.id !== membro.id))}>
                      <X aria-hidden className="size-3.5" />
                    </Button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </FormSection>
      </form>
    </Modal>
  )
}
