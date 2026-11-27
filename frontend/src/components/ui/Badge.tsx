import type { ReactNode } from 'react';

type Tone = 'neutral' | 'success' | 'primary';

const TONES: Record<Tone, string> = {
  neutral: 'ring-border text-text-muted',
  success: 'ring-success text-success',
  primary: 'ring-primary text-primary',
};

/** Selo em pílula, sobre fundo sólido para o texto manter o contraste AA. */
export function Badge({ tone = 'neutral', children }: { tone?: Tone; children: ReactNode }) {
  return (
    <span
      className={`inline-flex items-center rounded-control bg-surface px-2.5 py-0.5 text-sm font-semibold ring-1 ring-inset ${TONES[tone]}`}
    >
      {children}
    </span>
  );
}
