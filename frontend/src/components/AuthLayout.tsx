import type { ReactNode } from 'react';
import { Brand } from './Brand';

/**
 * Moldura de entrar e criar conta (research R56): no celular, só o cartão (a marca vem do cabeçalho);
 * no tablet, iPad e computador, duas metades — a marca em vermelho com detalhes dourados à
 * esquerda e o formulário à direita.
 */
export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="py-6 md:grid md:min-h-dvh md:grid-cols-2 md:py-0">
      <aside
        data-testid="auth-brand"
        className="auth-brand relative hidden flex-col items-center justify-center overflow-hidden p-10 md:flex"
      >
        <span aria-hidden="true" className="auth-ornament auth-ornament-top" />
        <span aria-hidden="true" className="auth-ornament auth-ornament-bottom" />
        <Brand size="large" onRed />
        <span aria-hidden="true" className="mt-6 h-px w-40 bg-gold-soft" />
      </aside>
      <div className="flex items-center justify-center md:px-10 md:py-12">
        <section className="surface-glass w-full max-w-md rounded-card p-6 md:p-8">
          {children}
        </section>
      </div>
    </div>
  );
}
