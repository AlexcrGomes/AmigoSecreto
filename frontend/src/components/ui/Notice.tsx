import type { ReactNode } from 'react';
import { IconAlert, IconCheck } from './icons';

type NoticeTone = 'success' | 'info' | 'danger';

const TONES: Record<NoticeTone, { ring: string; icon: string; text: string }> = {
  success: { ring: 'ring-success/60', icon: 'text-success', text: 'text-text' },
  info: { ring: 'ring-primary/50', icon: 'text-primary', text: 'text-text' },
  danger: { ring: 'ring-danger/60', icon: 'text-danger', text: 'text-danger' },
};

interface NoticeProps {
  tone?: NoticeTone;
  /** `status` anuncia sem interromper; `alert` para erros. */
  role?: 'status' | 'alert';
  className?: string;
  children: ReactNode;
}

/** Faixa de aviso em vidro com ícone e texto: o significado nunca depende só da cor (FR-009). */
export function Notice({ tone = 'info', role = 'status', className = '', children }: NoticeProps) {
  const style = TONES[tone];
  const Icon = tone === 'success' ? IconCheck : IconAlert;
  return (
    <div
      role={role}
      className={`surface-glass flex items-start gap-3 rounded-3xl px-4 py-3 font-medium ring-1 ring-inset ${style.ring} ${style.text} ${className}`}
    >
      <Icon className={`mt-0.5 size-5 shrink-0 ${style.icon}`} />
      <p className="min-w-0">{children}</p>
    </div>
  );
}
