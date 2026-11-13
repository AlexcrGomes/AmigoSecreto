import type { ButtonHTMLAttributes } from 'react';
import { Spinner } from './Spinner';

type Variant = 'primary' | 'secondary' | 'danger' | 'ghost';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  /** Mostra o spinner e bloqueia novos cliques enquanto a ação roda. */
  loading?: boolean;
  fullWidth?: boolean;
}

const VARIANTS: Record<Variant, string> = {
  primary: 'bg-primary text-on-primary hover:bg-primary-hover',
  secondary: 'border border-border bg-surface text-text hover:bg-surface-muted',
  danger: 'border border-danger bg-surface text-danger hover:bg-surface-muted',
  ghost: 'bg-transparent text-primary hover:bg-surface-muted',
};

export function Button({
  variant = 'primary',
  loading = false,
  fullWidth = false,
  disabled,
  type = 'button',
  className = '',
  children,
  ...rest
}: ButtonProps) {
  return (
    <button
      type={type}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={[
        // Alvo de toque mínimo de 44×44 px (Constituição VI)
        'inline-flex min-h-11 min-w-11 items-center justify-center gap-2 rounded-control px-4 font-semibold',
        'transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary',
        'disabled:cursor-not-allowed disabled:opacity-60',
        VARIANTS[variant],
        fullWidth ? 'w-full' : '',
        className,
      ].join(' ')}
      {...rest}
    >
      {loading && <Spinner />}
      <span>{children}</span>
    </button>
  );
}
