import { Button as GlassButton, type ButtonProps as GlassButtonProps } from 'glisse';
import type { ButtonHTMLAttributes } from 'react';
import { Spinner } from './Spinner';

export type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'ghost';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  /** Mostra o spinner e bloqueia novos cliques enquanto a ação roda. */
  loading?: boolean;
  fullWidth?: boolean;
}

/** Como cada variante vira o botão da Glisse (contracts/ui-components.md). */
const GLISSE: Record<ButtonVariant, Pick<GlassButtonProps, 'variant' | 'destructive' | 'tint'>> = {
  primary: { variant: 'filled' },
  secondary: { variant: 'glass' },
  ghost: { variant: 'glass', tint: 'var(--color-primary)' },
  danger: { variant: 'glass', destructive: true },
};

/** Visual equivalente para links com cara de botão (que não podem ser o botão da Glisse). */
const LINK_VARIANTS: Record<ButtonVariant, string> = {
  primary: 'bg-primary-fill text-on-primary hover:bg-primary-hover',
  secondary: 'surface-glass text-text',
  danger: 'surface-glass text-danger',
  ghost: 'surface-glass text-primary',
};

/** Classes de um link em pílula, no mesmo formato do botão. */
export function buttonClasses(variant: ButtonVariant = 'primary', fullWidth = false): string {
  return [
    // Alvo de toque mínimo de 44×44 px (Constituição VI)
    'inline-flex min-h-11 min-w-11 items-center justify-center gap-2 rounded-control px-4 font-semibold',
    'transition-colors',
    LINK_VARIANTS[variant],
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
    <GlassButton
      {...GLISSE[variant]}
      type={type}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={['min-h-11 min-w-11 font-semibold', fullWidth ? 'w-full' : '', className]
        .filter(Boolean)
        .join(' ')}
      {...rest}
    >
      {loading && <Spinner />}
      <span>{children}</span>
    </GlassButton>
  );
}
