import type { MouseEvent } from 'react'
import { caminhoDoModulo, navegar } from '../plataforma/rotas'
import type { Eu } from '../plataforma/tipos'
import { IconeDoModulo } from './Icone'
import type { EstadoDoMenu } from './Menu'

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

export function nomeDoPerfil(perfil: string) {
  return NOMES_DOS_PERFIS[perfil] ?? perfil
}

function abrir(evento: MouseEvent<HTMLAnchorElement>) {
  if (evento.button !== 0 || evento.metaKey || evento.ctrlKey || evento.shiftKey || evento.altKey) return
  evento.preventDefault()
  navegar(evento.currentTarget.getAttribute('href') ?? '/')
}

export function Inicio({ eu, nome, menu }: { eu: Eu | null; nome: string; menu: EstadoDoMenu }) {
  const primeiroNome = nome.split(' ')[0]
  return (
    <div className="mx-auto max-w-5xl px-4 py-8 sm:px-8 sm:py-10">
      {eu?.tenant.nome && <p className="text-sm text-muted-foreground">{eu.tenant.nome}</p>}
      <h1 className="mt-1 text-2xl font-semibold tracking-tight">Olá, {primeiroNome}</h1>
      {eu && eu.perfis.length > 0 && (
        <p className="mt-1 text-sm text-muted-foreground">
          {eu.perfis.length === 1 ? 'Perfil' : 'Perfis'}: {eu.perfis.map(nomeDoPerfil).join(', ')}
        </p>
      )}

      <section className="mt-10" aria-labelledby="titulo-modulos">
        <h2 id="titulo-modulos" className="text-sm font-medium">Seus módulos</h2>

        {menu.estado === 'carregando' && (
          <div className="mt-3 grid gap-3 sm:grid-cols-2 lg:grid-cols-3" aria-hidden>
            {[0, 1, 2].map((i) => <div key={i} className="h-24 animate-pulse rounded-lg bg-muted" />)}
          </div>
        )}

        {menu.estado === 'erro' && (
          <p className="mt-3 text-sm text-muted-foreground">Não foi possível carregar os módulos agora.</p>
        )}

        {menu.estado === 'pronto' && menu.modulos.length === 0 && (
          <p className="mt-3 rounded-lg border border-dashed p-6 text-sm text-muted-foreground">
            Nenhum módulo liberado para o seu perfil ainda. Fale com o administrador da sua empresa.
          </p>
        )}

        {menu.estado === 'pronto' && menu.modulos.length > 0 && (
          <ul className="mt-3 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {menu.modulos.map((modulo) => (
              <li key={modulo.codigo}>
                <a
                  href={caminhoDoModulo(modulo.codigo)}
                  onClick={abrir}
                  className="flex h-full items-start gap-3 rounded-lg border bg-card p-4 transition-colors hover:border-ring/50 hover:bg-accent/40"
                >
                  <span className="grid size-9 shrink-0 place-items-center rounded-md bg-accent text-accent-foreground">
                    <IconeDoModulo nome={modulo.icone} className="size-[18px]" />
                  </span>
                  <span className="min-w-0">
                    <span className="block truncate font-medium">{modulo.nome}</span>
                    <span className="mt-0.5 block text-sm text-muted-foreground">
                      {modulo.disponivel
                        ? modulo.itensSubmenu.map((item) => item.nome).join(' · ') || 'Abrir módulo'
                        : 'Indisponível no momento'}
                    </span>
                  </span>
                </a>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}
