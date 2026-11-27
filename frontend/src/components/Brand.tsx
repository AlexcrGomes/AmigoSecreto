import { IconGift } from './ui/icons';

interface BrandProps {
  /** `large`: painel das telas de conta no desenho largo. */
  size?: 'normal' | 'large';
  /** Sobre fundo vermelho: texto branco. */
  onRed?: boolean;
  /** Só o selo, sem o nome (menu lateral compacto); o nome fica acessível. */
  compact?: boolean;
}

/** Marca do Amigo Secreto: selo vermelho com o presente dourado e o nome (revisão R55). */
export function Brand({ size = 'normal', onRed = false, compact = false }: BrandProps) {
  const large = size === 'large';
  return (
    <span className="inline-flex items-center gap-3">
      <span
        className={[
          'grid shrink-0 place-items-center rounded-2xl ring-1 ring-gold-soft ring-inset',
          onRed ? 'bg-on-primary/10 text-gold-soft' : 'bg-primary-fill text-gold-soft',
          large ? 'size-16' : 'size-10',
        ].join(' ')}
      >
        <IconGift className={large ? 'size-9' : 'size-6'} />
      </span>
      <span
        className={[
          'font-extrabold tracking-tight',
          onRed ? 'text-on-primary' : 'text-primary',
          large ? 'text-4xl' : 'text-lg',
          compact ? 'sr-only' : '',
        ].join(' ')}
      >
        Amigo Secreto
      </span>
    </span>
  );
}
