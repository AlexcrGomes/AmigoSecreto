import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ExternalLinkModal } from './ExternalLinkModal';

describe('ExternalLinkModal', () => {
  afterEach(() => vi.restoreAllMocks());

  it('fechado quando não há endereço', () => {
    render(<ExternalLinkModal url={null} onClose={vi.fn()} />);

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('mostra o endereço e avisa que é um site externo', () => {
    render(<ExternalLinkModal url="https://loja.com/caneca" onClose={vi.fn()} />);

    const dialog = screen.getByRole('dialog', { name: 'Abrir site externo?' });
    expect(dialog).toHaveTextContent('Você vai abrir um site externo:');
    expect(dialog).toHaveTextContent('https://loja.com/caneca');
    expect(dialog).toHaveTextContent('O Amigo Secreto não controla esse site.');
  });

  it('"Abrir" abre em outra aba com noopener e noreferrer e fecha', async () => {
    const open = vi.spyOn(window, 'open').mockReturnValue(null);
    const onClose = vi.fn();
    render(<ExternalLinkModal url="https://loja.com/caneca" onClose={onClose} />);

    await userEvent.click(screen.getByRole('button', { name: 'Abrir' }));

    expect(open).toHaveBeenCalledWith('https://loja.com/caneca', '_blank', 'noopener,noreferrer');
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('"Cancelar" só fecha', async () => {
    const open = vi.spyOn(window, 'open').mockReturnValue(null);
    const onClose = vi.fn();
    render(<ExternalLinkModal url="https://loja.com/caneca" onClose={onClose} />);

    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }));

    expect(open).not.toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
  });
});
