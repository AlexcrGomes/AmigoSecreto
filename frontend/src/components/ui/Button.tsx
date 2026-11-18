import type { ButtonHTMLAttributes } from 'react';
import { Spinner } from './Spinner';

export type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'ghost';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  /** Mostra o spinner e bloqueia novos cliques enquanto a ação roda. */
  loading?: boolean;
  fullWidth?: boolean;
}

const VARIANTS: Record<ButtonVariant, string> = {
  primary: 'bg-primary text-on-primary hover:bg-primary-hover',
  secondary: 'border border-border bg-surface text-text hover:bg-surface-muted',
  danger: 'border border-danger bg-surface text-danger hover:bg-surface-muted',
  ghost: 'bg-transparent text-primary hover:bg-surface-muted',
};

/** Classes do botão, também usadas em links com cara de botão. */
export function buttonClasses(variant: ButtonVariant = 'primary', fullWidth = false): string {
  return [
    // Alvo de toque mínimo de 44×44 px (Constituição VI)
    'inline-flex min-h-11 min-w-11 items-center justify-center gap-2 rounded-control px-4 font-semibold',
    'transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary',
    'disabled:cursor-not-allowed disabled:opacity-60',
    VARIANTS[variant],
    fullWidth ? 'w-full' : '',
  ].join(' ');
}

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
      className={`${buttonClasses(variant, fullWidth)} ${className}`}
      {...rest}
    >
      {loading && <Spinner />}
      <span>{children}</span>
    </button>
  );
}
