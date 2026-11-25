import { zodResolver } from '@hookform/resolvers/zod';
import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import type { GroupDetail, MyWishlist, WishItem } from '../../api/groups';
import { addWishItem, removeWishItem, updateWishItem } from '../../api/wishlist';
import { groupKey } from '../../hooks/useGroup';
import { applyFieldErrors, errorMessage } from '../../lib/errors';
import { wishItemSchema, type WishItemInput } from '../../lib/schemas';
import { Button } from '../ui/Button';
import { Card } from '../ui/Card';
import { ConfirmModal } from '../ui/ConfirmModal';
import { Input } from '../ui/Input';
import { Modal } from '../ui/Modal';
import { useToast } from '../ui/Toast';

/** `null` = fechado; `'new'` = adicionando; um item = editando. */
type Editing = WishItem | 'new' | null;

/**
 * "Minha lista de desejos" (US1 da 003): até 5 sugestões, com descrição e link opcional. Só o próprio usuário
 * e quem o tirar no sorteio veem a lista (FR-009). Texto sempre como texto, nunca HTML (FR-008).
 */
export function WishlistEditor({ group }: { group: GroupDetail }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const { items, maxItems, editable } = group.myWishlist;
  const [editing, setEditing] = useState<Editing>(null);
  const [removing, setRemoving] = useState<WishItem | null>(null);

  /** Troca a lista guardada na consulta do grupo pela devolvida pelo servidor. */
  function replaceList(list: MyWishlist) {
    queryClient.setQueryData<GroupDetail>(groupKey(group.id), (old) =>
      old ? { ...old, myWishlist: list } : old,
    );
  }

  async function save(values: WishItemInput) {
    const body = { description: values.description, url: values.url === '' ? null : values.url };
    const list =
      editing === 'new' || editing === null
        ? await addWishItem(group.id, body)
        : await updateWishItem(group.id, editing.id, body);
    replaceList(list);
    toast.show(editing === 'new' ? 'Sugestão adicionada.' : 'Sugestão atualizada.', 'success');
    setEditing(null);
  }

  async function confirmRemove() {
    if (!removing) return;
    await removeWishItem(group.id, removing.id);
    replaceList({ ...group.myWishlist, items: items.filter((item) => item.id !== removing.id) });
    toast.show('Sugestão removida.', 'success');
    setRemoving(null);
  }

  const full = items.length >= maxItems;

  return (
    <Card>
      <div className="flex items-start justify-between gap-3">
        <h2 id="minha-lista" className="text-lg font-semibold text-text">
          Minha lista de desejos
        </h2>
        <span className="text-sm text-text-muted">
          {items.length} de {maxItems}
        </span>
      </div>
      <p className="mt-1 text-sm text-text-muted">Só você e quem tirar você veem esta lista.</p>

      {items.length === 0 ? (
        <p className="mt-4 text-text">Você ainda não sugeriu presentes. Ajude quem tirar você.</p>
      ) : (
        <ul aria-labelledby="minha-lista" className="mt-4 flex flex-col gap-3">
          {items.map((item) => (
            <li key={item.id} className="rounded-control border border-border p-3">
              <p className="font-medium break-words whitespace-pre-line text-text">
                {item.description}
              </p>
              {item.url && <p className="mt-1 text-sm break-all text-text-muted">{item.url}</p>}
              {editable && (
                <div className="mt-3 grid grid-cols-2 gap-2">
                  <Button
                    variant="secondary"
                    aria-label={`Editar ${item.description}`}
                    onClick={() => setEditing(item)}
                  >
                    Editar
                  </Button>
                  <Button
                    variant="ghost"
                    aria-label={`Remover ${item.description}`}
                    onClick={() => setRemoving(item)}
                  >
                    Remover
                  </Button>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}

      {editable &&
        (full ? (
          <p className="mt-4 text-sm text-text-muted">A lista tem no máximo 5 sugestões.</p>
        ) : (
          <Button fullWidth className="mt-4" onClick={() => setEditing('new')}>
            Adicionar sugestão
          </Button>
        ))}

      <Modal
        open={editing !== null}
        title={editing === 'new' ? 'Nova sugestão' : 'Editar sugestão'}
        onClose={() => setEditing(null)}
      >
        {editing !== null && (
          <WishItemForm
            initial={editing === 'new' ? null : editing}
            onSubmit={save}
            onCancel={() => setEditing(null)}
          />
        )}
      </Modal>

      <ConfirmModal
        open={removing !== null}
        title="Remover sugestão?"
        confirmLabel="Remover"
        onConfirm={confirmRemove}
        onClose={() => setRemoving(null)}
      >
        <p className="break-words">“{removing?.description}” sairá da sua lista.</p>
      </ConfirmModal>
    </Card>
  );
}

interface WishItemFormProps {
  initial: WishItem | null;
  /** Salva; um erro lançado aparece no formulário. */
  onSubmit: (values: WishItemInput) => Promise<void>;
  onCancel: () => void;
}

function WishItemForm({ initial, onSubmit, onCancel }: WishItemFormProps) {
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<WishItemInput>({
    resolver: zodResolver(wishItemSchema),
    defaultValues: { description: initial?.description ?? '', url: initial?.url ?? '' },
  });

  const submit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await onSubmit(values);
    } catch (error) {
      applyFieldErrors(error, setError, ['description', 'url']);
      setFormError(errorMessage(error));
    }
  });

  return (
    <form noValidate onSubmit={submit} className="flex flex-col gap-4">
      {formError && (
        <p role="alert" className="text-danger">
          {formError}
        </p>
      )}
      <Input
        label="Sugestão"
        placeholder="Ex.: livro de receitas"
        autoComplete="off"
        maxLength={120}
        error={errors.description?.message}
        {...register('description')}
      />
      <Input
        label="Link (opcional)"
        type="url"
        inputMode="url"
        placeholder="https://"
        autoComplete="off"
        error={errors.url?.message}
        {...register('url')}
      />
      <Button type="submit" fullWidth loading={isSubmitting}>
        Salvar
      </Button>
      <Button variant="ghost" fullWidth disabled={isSubmitting} onClick={onCancel}>
        Cancelar
      </Button>
    </form>
  );
}
