import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { WishlistView } from './WishlistView';

describe('WishlistView', () => {
  afterEach(() => vi.restoreAllMocks());

  it('mostra os itens de quem o usuário tirou, na ordem', () => {
    render(
      <WishlistView
        wishlist={[
          { description: 'Livro de receitas', url: null },
          { description: 'Caneca', url: 'https://loja.com/caneca' },
        ]}
        updatedAt={null}
      />,
    );

    const list = screen.getByRole('list', { name: 'Lista de desejos' });
    const items = within(list).getAllByRole('listitem');
    expect(items[0]).toHaveTextContent('Livro de receitas');
    expect(items[1]).toHaveTextContent('Caneca');
    expect(screen.queryByText(/Atualizada em/)).not.toBeInTheDocument();
  });

  it('lista vazia avisa que não há sugestões', () => {
    render(<WishlistView wishlist={[]} updatedAt={null} />);

    expect(screen.getByText('Ainda não há sugestões de presente.')).toBeInTheDocument();
  });

  it('mostra "Atualizada em dd/mm" quando a lista mudou depois do sorteio', () => {
    render(
      <WishlistView
        wishlist={[{ description: 'Livro', url: null }]}
        updatedAt="2026-12-21T15:00:00Z"
      />,
    );

    expect(screen.getByText('Atualizada em 21/12')).toBeInTheDocument();
  });

  it('o link só abre depois do aviso, em outra aba e sem dizer de onde veio', async () => {
    const open = vi.spyOn(window, 'open').mockReturnValue(null);
    render(
      <WishlistView
        wishlist={[{ description: 'Caneca', url: 'https://loja.com/caneca' }]}
        updatedAt={null}
      />,
    );

    await userEvent.click(screen.getByRole('button', { name: 'Abrir o link de Caneca' }));

    const dialog = screen.getByRole('dialog', { name: 'Abrir site externo?' });
    expect(dialog).toHaveTextContent('https://loja.com/caneca');
    expect(open).not.toHaveBeenCalled();

    await userEvent.click(within(dialog).getByRole('button', { name: 'Abrir' }));

    expect(open).toHaveBeenCalledWith('https://loja.com/caneca', '_blank', 'noopener,noreferrer');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('"Cancelar" fecha sem abrir nada', async () => {
    const open = vi.spyOn(window, 'open').mockReturnValue(null);
    render(
      <WishlistView
        wishlist={[{ description: 'Caneca', url: 'https://loja.com/caneca' }]}
        updatedAt={null}
      />,
    );

    await userEvent.click(screen.getByRole('button', { name: 'Abrir o link de Caneca' }));
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }));

    expect(open).not.toHaveBeenCalled();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('texto com HTML aparece como texto', () => {
    render(
      <WishlistView
        wishlist={[{ description: '<img src=x onerror=alert(1)>', url: null }]}
        updatedAt={null}
      />,
    );

    expect(screen.getByText('<img src=x onerror=alert(1)>')).toBeInTheDocument();
    expect(document.querySelector('img')).toBeNull();
  });
});
