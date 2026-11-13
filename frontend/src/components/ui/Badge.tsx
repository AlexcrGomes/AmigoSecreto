import type { ReactNode } from 'react';

type Tone = 'neutral' | 'success' | 'primary';

const TONES: Record<Tone, string> = {
  neutral: 'border-border text-text-muted',
  success: 'border-success text-success',
  primary: 'border-primary text-primary',
};

export function Badge({ tone = 'neutral', children }: { tone?: Tone; children: ReactNode }) {
  return (
    <span
      className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-sm font-medium ${TONES[tone]}`}
    >
      {children}
    </span>
  );
}
