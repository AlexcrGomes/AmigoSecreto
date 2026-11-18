import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { Badge } from './Badge';
import { Card } from './Card';
import { Spinner } from './Spinner';
import { ToastProvider, useToast } from './Toast';

function Trigger() {
  const toast = useToast();
  return (
    <>
      <button onClick={() => toast.show('Código copiado!', 'success')}>sucesso</button>
      <button onClick={() => toast.show('Falhou.', 'error')}>erro</button>
    </>
  );
}

describe('Toast', () => {
  afterEach(() => vi.useRealTimers());

  it('anuncia a mensagem numa região aria-live e permite fechar', async () => {
    render(
      <ToastProvider>
        <Trigger />
      </ToastProvider>,
    );

    await userEvent.click(screen.getByRole('button', { name: 'sucesso' }));

    expect(screen.getByRole('status')).toHaveAttribute('aria-live', 'polite');
    expect(screen.getByText('Código copiado!')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Fechar aviso' }));
    expect(screen.queryByText('Código copiado!')).not.toBeInTheDocument();
  });

  it('some sozinho depois de alguns segundos', () => {
    vi.useFakeTimers();
    render(
      <ToastProvider>
        <Trigger />
      </ToastProvider>,
    );

    act(() => screen.getByRole('button', { name: 'erro' }).click());
    expect(screen.getByText('Falhou.')).toBeInTheDocument();

    act(() => vi.advanceTimersByTime(5000));
    expect(screen.queryByText('Falhou.')).not.toBeInTheDocument();
  });

  it('useToast fora do provider é um erro de programação', () => {
    vi.spyOn(console, 'error').mockImplementation(() => {});

    expect(() => render(<Trigger />)).toThrow('useToast precisa estar dentro de <ToastProvider>');
  });
});

describe('Card, Badge e Spinner', () => {
  it('renderizam o conteúdo com acessibilidade', () => {
    render(
      <Card aria-label="Grupo">
        <Badge tone="success">Sorteado</Badge>
        <Badge>Aberto</Badge>
        <Spinner label="Carregando grupos" />
      </Card>,
    );

    expect(screen.getByLabelText('Grupo')).toBeInTheDocument();
    expect(screen.getByText('Sorteado')).toBeInTheDocument();
    expect(screen.getByText('Aberto')).toBeInTheDocument();
    expect(screen.getByRole('status', { name: 'Carregando grupos' })).toBeInTheDocument();
  });

  it('spinner sem rótulo é decorativo', () => {
    render(<Spinner />);

    expect(screen.getByTestId('spinner')).toHaveAttribute('aria-hidden', 'true');
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });
});
