import type { HTMLAttributes } from 'react';

export function Card({ className = '', children, ...rest }: HTMLAttributes<HTMLElement>) {
  return (
    <section className={`rounded-card bg-surface p-5 shadow-card ${className}`} {...rest}>
      {children}
    </section>
  );
}
