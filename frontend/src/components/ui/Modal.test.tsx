import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { Button } from './Button';
import { Modal } from './Modal';

function Harness({ onClose }: { onClose?: () => void }) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <Button onClick={() => setOpen(true)}>Abrir</Button>
      <Modal
        open={open}
        title="Confirmar sorteio"
        onClose={() => {
          onClose?.();
          setOpen(false);
        }}
        footer={
          <>
            <Button variant="secondary" onClick={() => setOpen(false)}>
              Cancelar
            </Button>
            <Button>Sortear</Button>
          </>
        }
      >
        Ninguém mais poderá entrar ou sair.
      </Modal>
    </>
  );
}

function NoFocusables() {
  const [open, setOpen] = useState(false);
  return (
    <>
      <Button onClick={() => setOpen(true)}>Abrir</Button>
      <Modal open={open} title="Aviso" onClose={() => setOpen(false)}>
        Só texto.
      </Modal>
    </>
  );
}

describe('Modal', () => {
  it('fechado não renderiza nada', () => {
    render(<Modal open={false} title="X" onClose={() => {}} />);

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('usa role dialog com título acessível e foca o primeiro elemento', async () => {
    render(<Harness />);

    await userEvent.click(screen.getByRole('button', { name: 'Abrir' }));

    const dialog = screen.getByRole('dialog', { name: 'Confirmar sorteio' });
    expect(dialog).toHaveAttribute('aria-modal', 'true');
    expect(screen.getByText('Ninguém mais poderá entrar ou sair.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Cancelar' })).toHaveFocus();
  });

  it('prende o foco dentro do diálogo com Tab e Shift+Tab', async () => {
    render(<Harness />);
    await userEvent.click(screen.getByRole('button', { name: 'Abrir' }));

    await userEvent.tab();
    expect(screen.getByRole('button', { name: 'Sortear' })).toHaveFocus();

    await userEvent.tab();
    expect(screen.getByRole('button', { name: 'Cancelar' })).toHaveFocus();

    await userEvent.tab({ shift: true });
    expect(screen.getByRole('button', { name: 'Sortear' })).toHaveFocus();
  });

  it('fecha com Esc e devolve o foco ao gatilho', async () => {
    const onClose = vi.fn();
    render(<Harness onClose={onClose} />);
    const trigger = screen.getByRole('button', { name: 'Abrir' });
    await userEvent.click(trigger);

    await userEvent.keyboard('{Escape}');

    expect(onClose).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
  });

  it('fecha ao tocar fora do diálogo', async () => {
    const onClose = vi.fn();
    render(<Harness onClose={onClose} />);
    await userEvent.click(screen.getByRole('button', { name: 'Abrir' }));

    await userEvent.click(screen.getByTestId('modal-backdrop'));

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('sem elementos focáveis, o foco fica no próprio diálogo', async () => {
    render(<NoFocusables />);
    await userEvent.click(screen.getByRole('button', { name: 'Abrir' }));

    expect(screen.getByRole('dialog')).toHaveFocus();
    await userEvent.tab();
    expect(screen.getByRole('dialog')).toHaveFocus();
  });
});
