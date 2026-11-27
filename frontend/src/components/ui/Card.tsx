import { Glass } from 'glisse';
import type { HTMLAttributes } from 'react';

interface CardProps extends HTMLAttributes<HTMLElement> {
  /** Cartão em destaque (ex.: o amigo secreto): superfície de vidro da Glisse, com refração. */
  highlight?: boolean;
}

/** Cartão de vidro fosco (research R47): o nível comum é CSS; o destaque usa a Glisse. */
export function Card({ highlight = false, className = '', children, ...rest }: CardProps) {
  if (highlight) {
    return (
      <Glass className={`rounded-card p-5 text-text [--lg-blur:20px] ${className}`} {...rest}>
        {children}
      </Glass>
    );
  }
  return (
    <section className={`surface-glass rounded-card p-5 text-text ${className}`} {...rest}>
      {children}
    </section>
  );
}
