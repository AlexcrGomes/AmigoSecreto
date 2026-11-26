import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ANA, mockSession, renderWithProviders } from '../../test/render';
import { DependentForm } from './DependentForm';

describe('DependentForm', () => {
  beforeEach(() => mockSession(ANA));

  it('envia o nome sem os espaços nas pontas', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    renderWithProviders(<DependentForm onSubmit={onSubmit} onCancel={vi.fn()} />);

    await userEvent.type(screen.getByLabelText('Nome'), '  Bia  ');
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }));

    expect(onSubmit).toHaveBeenCalledWith('Bia');
  });

  it.each([
    ['', 'vazio'],
    ['L', 'curto demais'],
  ])('recusa nome %s (%s)', async (name) => {
    const onSubmit = vi.fn();
    renderWithProviders(<DependentForm onSubmit={onSubmit} onCancel={vi.fn()} />);

    if (name) await userEvent.type(screen.getByLabelText('Nome'), name);
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }));

    expect(await screen.findByText('Informe um nome de 2 a 80 caracteres.')).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('cancelar não envia nada', async () => {
    const onSubmit = vi.fn();
    const onCancel = vi.fn();
    renderWithProviders(
      <DependentForm initialName="Lucas" onSubmit={onSubmit} onCancel={onCancel} />,
    );

    expect(screen.getByLabelText('Nome')).toHaveValue('Lucas');
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }));

    expect(onCancel).toHaveBeenCalled();
    expect(onSubmit).not.toHaveBeenCalled();
  });
});
