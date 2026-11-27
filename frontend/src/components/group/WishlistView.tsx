import { useState } from 'react';
import type { FriendWishItem } from '../../api/groups';
import { ExternalLinkModal } from './ExternalLinkModal';

const updatedDate = new Intl.DateTimeFormat('pt-BR', {
  day: '2-digit',
  month: '2-digit',
  timeZone: 'America/Sao_Paulo',
});

interface WishlistViewProps {
  wishlist: FriendWishItem[];
  /** Última alteração depois do sorteio, ou `null` (FR-015a). */
  updatedAt: string | null;
}

/**
 * A lista de desejos de quem o usuário tirou, dentro de "Seu amigo secreto" (US2 da 003). Vem junto do nome e
 * some junto com ele (FR-011). Texto sempre como texto (FR-008); links abrem só depois do aviso (FR-017).
 */
export function WishlistView({ wishlist, updatedAt }: WishlistViewProps) {
  const [opening, setOpening] = useState<string | null>(null);

  return (
    <div className="mt-2 mb-4 text-left">
      <div className="flex items-baseline justify-between gap-3">
        <h3 id="lista-do-amigo" className="font-semibold text-text">
          Lista de desejos
        </h3>
        {updatedAt && (
          <span className="text-sm text-text-muted">
            Atualizada em {updatedDate.format(new Date(updatedAt))}
          </span>
        )}
      </div>
      {wishlist.length === 0 ? (
        <p className="mt-2 text-text-muted">Ainda não há sugestões de presente.</p>
      ) : (
        <ul aria-labelledby="lista-do-amigo" className="mt-2 flex flex-col gap-2">
          {wishlist.map((item, index) => (
            <li key={index} className="rounded-2xl bg-surface/60 p-3 ring-1 ring-border ring-inset">
              <p className="break-words whitespace-pre-line text-text">{item.description}</p>
              {item.url && (
                <button
                  type="button"
                  className="mt-1 min-h-11 text-left text-sm break-all text-primary underline"
                  aria-label={`Abrir o link de ${item.description}`}
                  onClick={() => setOpening(item.url)}
                >
                  {item.url}
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
      <ExternalLinkModal url={opening} onClose={() => setOpening(null)} />
    </div>
  );
}
