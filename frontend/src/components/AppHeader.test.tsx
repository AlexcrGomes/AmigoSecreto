import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ANA, mockSession, renderWithProviders } from '../test/render';
import { AppHeader } from './AppHeader';

/** Cabeçalho compacto do celular: só a marca. A navegação e o "Sair" ficam na AppNavigation. */
describe('AppHeader', () => {
  it('sem sessão mostra só o nome do app', async () => {
    mockSession(null);
    renderWithProviders(<AppHeader />);

    expect(screen.getByRole('banner')).toHaveTextContent('Amigo Secreto');
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(screen.queryByRole('button', { name: 'Sair' })).not.toBeInTheDocument();
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
  });

  it('com sessão continua só com a marca, que leva para Meus grupos', async () => {
    mockSession(ANA);
    renderWithProviders(<AppHeader />, { route: '/grupos/1' });

    const home = screen.getByRole('link', { name: 'Amigo Secreto' });
    expect(home).toHaveAttribute('href', '/');
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(screen.queryByRole('button', { name: 'Sair' })).not.toBeInTheDocument();
  });
});
