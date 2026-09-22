import type { ReactNode } from 'react'
import { Check, X } from 'lucide-react'
import { cx } from '@/components/ui'
import { Marca } from '../../componentes/Marca'
import { regrasDaSenha } from '../../plataforma/formato'

/** Moldura das telas sem sessão: marca à esquerda (em telas largas) e o formulário à direita. */
export function TelaPublica({ children }: { children: ReactNode }) {
  return (
    <main className="grid min-h-dvh bg-fundo lg:grid-cols-2">
      <section className="hidden flex-col justify-between bg-brand-950 p-10 text-white lg:flex">
        <Marca />
        <div>
          <h2 className="max-w-md text-3xl font-bold tracking-tight">Gestão comercial, financeira e de serviços num lugar só.</h2>
          <p className="mt-3 max-w-md text-sm text-brand-300">
            CRM, contratos, financeiro, chamados e marketing da sua empresa, com um único acesso.
          </p>
        </div>
        <p className="text-xs text-brand-300">Centinela Soluções</p>
      </section>
      <section className="flex items-center justify-center px-4 py-10 sm:px-8">
        <div className="w-full max-w-sm">
          <Marca sobreEscuro={false} className="mb-8 lg:hidden" />
          {children}
        </div>
      </section>
    </main>
  )
}

/** O que a senha nova ainda precisa — a mesma regra que o identity aplica (RF09). */
export function RegrasDaSenha({ senha }: { senha: string }) {
  const regras = regrasDaSenha(senha)
  const itens: [boolean, string][] = [
    [regras.tamanho, 'Pelo menos 8 caracteres'],
    [regras.letraENumero, 'Letras e números'],
  ]
  if (!regras.limite) itens.push([false, 'No máximo 72 bytes (senha longa demais)'])
  return (
    <ul className="grid gap-1 text-xs" aria-label="Regras da senha">
      {itens.map(([cumprida, texto]) => (
        <li key={texto} className={cx('flex items-center gap-1.5', cumprida ? 'text-brand-700 dark:text-brand-400' : 'text-texto-3')}>
          {cumprida ? <Check aria-hidden className="size-3.5" /> : <X aria-hidden className="size-3.5" />}
          {texto}
          <span className="sr-only">{cumprida ? '(atendida)' : '(pendente)'}</span>
        </li>
      ))}
    </ul>
  )
}
