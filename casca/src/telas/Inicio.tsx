import type { ReactNode } from 'react'
import { ChevronRight, UserRound } from 'lucide-react'
import { Badge, PageHeader } from '@/components/ui'
import { IconeDoModulo } from '../componentes/Icone'
import { Link } from '../componentes/Link'
import { itensDeAdministracao, useCasca } from '../plataforma/contexto'
import { caminhoDoModulo } from '../plataforma/rotas'

const NOMES_DOS_PERFIS: Record<string, string> = {
  ADMINISTRADOR: 'Administrador',
  GESTOR: 'Gestor',
  VENDEDOR: 'Vendedor',
  PRE_VENDAS: 'Pré-vendas',
  FINANCEIRO: 'Financeiro',
  TECNICO: 'Técnico',
  CONTABILIDADE: 'Contabilidade',
  MARKETING: 'Marketing',
  PARCEIRO: 'Parceiro',
  CLIENTE: 'Cliente',
}

export const nomeDoPerfil = (perfil: string) => NOMES_DOS_PERFIS[perfil] ?? perfil

export function Inicio() {
  const { sessao, eu, menu, tem } = useCasca()
  const primeiroNome = sessao.usuario.nome.split(' ')[0]
  const administracao = itensDeAdministracao(tem)
  const subtitulo = [eu?.tenant.nome, eu?.perfis.map(nomeDoPerfil).join(', ')].filter(Boolean).join(' · ')

  return (
    <div className="flex flex-1 flex-col">
      <PageHeader title={`Olá, ${primeiroNome}`} subtitle={subtitulo || undefined} />
      <div className="grid gap-8 p-5">
        <Secao titulo="Seus módulos">
          {menu.estado === 'carregando' &&
            [0, 1, 2].map((i) => <li key={i} aria-hidden className="h-24 animate-pulse rounded-xl bg-superficie-2" />)}
          {menu.estado === 'erro' && (
            <li className="text-sm text-texto-3 sm:col-span-2 lg:col-span-3">Não foi possível carregar os módulos agora.</li>
          )}
          {menu.estado === 'pronto' && menu.modulos.length === 0 && (
            <li className="rounded-xl border border-dashed border-borda-forte p-6 text-sm text-texto-3 sm:col-span-2 lg:col-span-3">
              Nenhum módulo liberado para o seu perfil ainda. Fale com o administrador da sua empresa.
            </li>
          )}
          {menu.estado === 'pronto' &&
            menu.modulos.map((modulo) => (
              <Cartao
                key={modulo.codigo}
                href={caminhoDoModulo(modulo.codigo)}
                icone={<IconeDoModulo nome={modulo.icone} className="size-5" />}
                titulo={modulo.nome}
                selo={!modulo.disponivel && <Badge variant="red">indisponível</Badge>}
              >
                {modulo.itensSubmenu.map((item) => item.nome).join(' · ') || 'Abrir o módulo'}
              </Cartao>
            ))}
        </Secao>

        {administracao.length > 0 && (
          <Secao titulo="Administração">
            {administracao.map((item) => (
              <Cartao key={item.caminho} href={item.caminho} icone={<item.icone aria-hidden className="size-5" />} titulo={item.nome}>
                {item.caminho === '/admin/usuarios' && 'Cadastro, convite e desativação'}
                {item.caminho === '/admin/perfis' && 'Perfis e matriz de permissões'}
                {item.caminho === '/admin/equipes' && 'Membros e líderes das equipes'}
              </Cartao>
            ))}
          </Secao>
        )}

        <Secao titulo="Sua conta">
          <Cartao href="/conta" icone={<UserRound aria-hidden className="size-5" />} titulo="Minha conta">
            Dados, senha e sessões abertas
          </Cartao>
        </Secao>
      </div>
    </div>
  )
}

function Secao({ titulo, children }: { titulo: string; children: ReactNode }) {
  return (
    <section aria-label={titulo}>
      <h2 className="mb-3 text-xs font-semibold tracking-wider text-texto-3 uppercase">{titulo}</h2>
      <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">{children}</ul>
    </section>
  )
}

function Cartao({ href, icone, titulo, selo, children }: { href: string; icone: ReactNode; titulo: string; selo?: ReactNode; children: ReactNode }) {
  return (
    <li>
      <Link
        href={href}
        className="group flex h-full items-start gap-3 rounded-xl border border-borda bg-superficie p-4 shadow-sm transition-colors hover:border-brand-700/40 hover:bg-brand-50 dark:hover:bg-superficie-2"
      >
        <span className="grid size-10 shrink-0 place-items-center rounded-lg bg-brand-950 text-brand-400">{icone}</span>
        <span className="min-w-0 flex-1">
          <span className="flex items-center gap-2">
            <span className="truncate font-semibold text-titulo">{titulo}</span>
            {selo}
          </span>
          <span className="mt-0.5 block text-sm text-texto-3">{children}</span>
        </span>
        <ChevronRight aria-hidden className="mt-1 size-4 shrink-0 text-texto-3 transition-transform group-hover:translate-x-0.5" />
      </Link>
    </li>
  )
}
