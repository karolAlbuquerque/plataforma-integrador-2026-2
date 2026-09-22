import { useState, type FormEvent } from 'react'
import { ChevronRight, Copy, Plus, RotateCw, ShieldCheck, Trash2 } from 'lucide-react'
import { Badge, Button, EmptyState, FormField, FormSection, Input, Modal, PageHeader, Spinner, Textarea } from '@/components/ui'
import { ErroDaApi, api, mensagemDe } from '../../plataforma/api'
import { avisar } from '../../plataforma/avisos'
import { useCarregar, useCasca } from '../../plataforma/contexto'
import { navegar } from '../../plataforma/rotas'
import type { Pagina, PerfilDetalhe, PerfilResumo } from '../../plataforma/tipos'

type Dialogo =
  | { tipo: 'novo' }
  | { tipo: 'duplicar'; perfil: PerfilResumo }
  | { tipo: 'excluir'; perfil: PerfilResumo }

/**
 * Perfis da empresa (RF21): os dez de sistema e os criados aqui. A matriz de permissões de cada um
 * abre em /admin/perfis/{id}. Perfil de sistema não é excluído nem renomeado; perfil com usuários
 * também não sai.
 */
export function Perfis() {
  const { tem } = useCasca()
  const administra = tem('identity.perfil.administrar')
  const { carga, atualizando, recarregar } = useCarregar(
    () => api.get<Pagina<PerfilResumo>>('/api/identity/perfis?tamanho=100').then((p) => p.itens),
    [],
  )
  const [dialogo, setDialogo] = useState<Dialogo | null>(null)
  const perfis = carga.tipo === 'pronto' ? carga.dados : []

  return (
    <div className="flex flex-1 flex-col">
      <PageHeader
        title="Perfis de acesso"
        subtitle={carga.tipo === 'pronto' ? `${perfis.length} perfis · as permissões de cada usuário são a soma dos perfis dele` : 'Carregando…'}
        actions={administra && (
          <Button size="sm" onClick={() => setDialogo({ tipo: 'novo' })}>
            <Plus aria-hidden className="size-4" /> Novo perfil
          </Button>
        )}
      />
      <div className="flex-1 p-5">
        {carga.tipo === 'carregando' && <Spinner />}
        {carga.tipo === 'erro' && (
          <EmptyState title="Não foi possível carregar os perfis" action={<Button variant="outline" onClick={recarregar}><RotateCw aria-hidden className="size-4" /> Tentar de novo</Button>}>
            {carga.mensagem}
          </EmptyState>
        )}
        {carga.tipo === 'pronto' && (
          <div className={`overflow-hidden rounded-xl border border-borda bg-superficie shadow-sm ${atualizando ? 'opacity-60' : ''}`}>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="border-b border-borda bg-superficie-2 text-left text-xs font-semibold tracking-wide text-texto-3 uppercase">
                  <tr>
                    <th scope="col" className="px-4 py-2.5">Perfil</th>
                    <th scope="col" className="hidden px-4 py-2.5 lg:table-cell">Descrição</th>
                    <th scope="col" className="px-4 py-2.5 text-right">Usuários</th>
                    <th scope="col" className="hidden px-4 py-2.5 text-right sm:table-cell">Permissões</th>
                    <th scope="col" className="px-4 py-2.5"><span className="sr-only">Ações</span></th>
                  </tr>
                </thead>
                <tbody>
                  {perfis.map((perfil) => (
                    <tr key={perfil.id} onClick={() => navegar(`/admin/perfis/${perfil.id}`)} className="cursor-pointer border-b border-borda last:border-0 hover:bg-brand-50/60 dark:hover:bg-superficie-2">
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2">
                          <ShieldCheck aria-hidden className="size-4 shrink-0 text-brand-700" />
                          <button type="button" className="text-left font-medium text-titulo hover:underline">{perfil.rotulo}</button>
                          {perfil.sistema && <Badge>sistema</Badge>}
                        </div>
                      </td>
                      <td className="hidden max-w-md px-4 py-3 text-texto-2 lg:table-cell"><span className="line-clamp-1">{perfil.descricao ?? '—'}</span></td>
                      <td className="px-4 py-3 text-right text-texto-2 tabular-nums">{perfil.totalUsuarios}</td>
                      <td className="hidden px-4 py-3 text-right text-texto-2 tabular-nums sm:table-cell">{perfil.totalPermissoes}</td>
                      <td className="px-4 py-2">
                        <div className="flex justify-end gap-1" onClick={(e) => e.stopPropagation()}>
                          {administra && (
                            <Button variant="ghost" size="sm" aria-label={`Duplicar ${perfil.rotulo}`} title="Duplicar" onClick={() => setDialogo({ tipo: 'duplicar', perfil })}>
                              <Copy aria-hidden className="size-3.5" />
                            </Button>
                          )}
                          {administra && !perfil.sistema && (
                            <Button variant="ghost" size="sm" aria-label={`Excluir ${perfil.rotulo}`} title="Excluir" onClick={() => setDialogo({ tipo: 'excluir', perfil })}>
                              <Trash2 aria-hidden className="size-3.5" />
                            </Button>
                          )}
                          <ChevronRight aria-hidden className="size-4 self-center text-texto-3" />
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>

      {dialogo?.tipo === 'novo' && <NovoPerfil aoFechar={() => setDialogo(null)} />}
      {dialogo?.tipo === 'duplicar' && <Duplicar perfil={dialogo.perfil} aoFechar={() => setDialogo(null)} />}
      {dialogo?.tipo === 'excluir' && (
        <Excluir perfil={dialogo.perfil} aoFechar={() => setDialogo(null)} aoExcluir={() => { setDialogo(null); recarregar() }} />
      )}
    </div>
  )
}

/** Cria o perfil vazio e abre a matriz para marcar as permissões (RF22). */
function NovoPerfil({ aoFechar }: { aoFechar: () => void }) {
  const [nome, setNome] = useState('')
  const [descricao, setDescricao] = useState('')
  const [erros, setErros] = useState<Record<string, string>>({})
  const [salvando, setSalvando] = useState(false)

  async function criar(evento: FormEvent) {
    evento.preventDefault()
    setSalvando(true)
    setErros({})
    try {
      const perfil = await api.post<PerfilDetalhe>('/api/identity/perfis', { nome: nome.trim(), descricao: descricao.trim() || null, permissoes: [] })
      avisar('sucesso', `Perfil ${perfil.rotulo} criado. Marque as permissões dele.`)
      aoFechar()
      navegar(`/admin/perfis/${perfil.id}`)
    } catch (e) {
      if (e instanceof ErroDaApi && [400, 409, 422].includes(e.status)) setErros({ ...e.porCampo, geral: e.porCampo.nome ? '' : e.message })
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
      title="Novo perfil"
      subtitle="Depois de criar, você marca as permissões na matriz."
      footer={
        <>
          <Button variant="outline" onClick={aoFechar}>Cancelar</Button>
          <Button type="submit" form="novo-perfil" loading={salvando} disabled={!nome.trim()}>Criar e marcar permissões</Button>
        </>
      }
    >
      <form id="novo-perfil" onSubmit={criar} noValidate>
        {erros.geral && <p role="alert" className="mb-4 text-sm text-red-600 dark:text-red-400">{erros.geral}</p>}
        <FormSection number={1} title="Identificação">
          <FormField label="Nome" required colSpan={2} htmlFor="perfil-nome" error={erros.nome} hint="Único na empresa, por exemplo “Supervisor de suporte”.">
            <Input id="perfil-nome" autoFocus value={nome} maxLength={60} invalid={!!erros.nome} onChange={(e) => setNome(e.target.value)} />
          </FormField>
          <FormField label="Descrição" colSpan={2} htmlFor="perfil-descricao" error={erros.descricao}>
            <Textarea id="perfil-descricao" value={descricao} maxLength={300} onChange={(e) => setDescricao(e.target.value)} />
          </FormField>
        </FormSection>
      </form>
    </Modal>
  )
}

/** A cópia nunca é de sistema e só leva permissões que quem copia já tem. */
function Duplicar({ perfil, aoFechar }: { perfil: PerfilResumo; aoFechar: () => void }) {
  const [nome, setNome] = useState(`${perfil.rotulo} (cópia)`)
  const [erro, setErro] = useState<string | null>(null)
  const [salvando, setSalvando] = useState(false)

  async function duplicar(evento: FormEvent) {
    evento.preventDefault()
    setSalvando(true)
    setErro(null)
    try {
      const copia = await api.post<PerfilDetalhe>(`/api/identity/perfis/${perfil.id}/duplicar`, { nome: nome.trim() })
      avisar('sucesso', `Perfil ${copia.rotulo} criado a partir de ${perfil.rotulo}.`)
      aoFechar()
      navegar(`/admin/perfis/${copia.id}`)
    } catch (e) {
      setErro(e instanceof ErroDaApi ? e.porCampo.nome ?? e.message : mensagemDe(e))
    } finally {
      setSalvando(false)
    }
  }

  return (
    <Modal
      open
      onClose={aoFechar}
      size="sm"
      title={`Duplicar ${perfil.rotulo}`}
      footer={
        <>
          <Button variant="outline" onClick={aoFechar}>Cancelar</Button>
          <Button type="submit" form="duplicar-perfil" loading={salvando} disabled={!nome.trim()}>Duplicar</Button>
        </>
      }
    >
      <form id="duplicar-perfil" onSubmit={duplicar} noValidate>
        <FormField label="Nome da cópia" required htmlFor="copia-nome" error={erro}>
          <Input id="copia-nome" autoFocus value={nome} maxLength={60} invalid={!!erro} onChange={(e) => setNome(e.target.value)} />
        </FormField>
        <p className="mt-3 text-sm text-texto-3">A cópia leva as {perfil.totalPermissoes} permissões e a descrição. Nenhum usuário é movido.</p>
      </form>
    </Modal>
  )
}

function Excluir({ perfil, aoFechar, aoExcluir }: { perfil: PerfilResumo; aoFechar: () => void; aoExcluir: () => void }) {
  const [erro, setErro] = useState<string | null>(null)
  const [excluindo, setExcluindo] = useState(false)

  async function excluir() {
    setExcluindo(true)
    setErro(null)
    try {
      const mensagem = await api.acao('DELETE', `/api/identity/perfis/${perfil.id}`)
      avisar('sucesso', mensagem ?? 'Perfil excluído.')
      aoExcluir()
    } catch (e) {
      setErro(mensagemDe(e))
    } finally {
      setExcluindo(false)
    }
  }

  return (
    <Modal
      open
      onClose={aoFechar}
      size="sm"
      title={`Excluir ${perfil.rotulo}?`}
      footer={
        <>
          <Button variant="outline" onClick={aoFechar}>Cancelar</Button>
          <Button variant="danger" loading={excluindo} onClick={() => void excluir()}>
            <Trash2 aria-hidden className="size-3.5" /> Excluir
          </Button>
        </>
      }
    >
      <p className="text-sm text-texto-2">
        {perfil.totalUsuarios > 0
          ? `${perfil.totalUsuarios === 1 ? '1 usuário ainda tem' : `${perfil.totalUsuarios} usuários ainda têm`} este perfil — troque o perfil deles antes.`
          : 'O perfil sai da lista. A auditoria continua registrando quem o criou e quem o excluiu.'}
      </p>
      {erro && <p role="alert" className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-500/15 dark:text-red-300">{erro}</p>}
    </Modal>
  )
}
