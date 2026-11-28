import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';
import { AuthLayout } from './AuthLayout';

function renderLayout() {
  return render(
    <MemoryRouter>
      <AuthLayout>
        <h1>Entrar</h1>
      </AuthLayout>
    </MemoryRouter>,
  );
}

describe('AuthLayout', () => {
  it('põe o formulário num cartão de vidro', () => {
    renderLayout();

    const heading = screen.getByRole('heading', { name: 'Entrar' });
    expect(heading.closest('.surface-glass')).not.toBeNull();
  });

  it('no tablet e no computador, marca e formulário ficam lado a lado (duas metades)', () => {
    const { container } = renderLayout();

    const grid = container.firstElementChild as HTMLElement;
    expect(grid).toHaveClass('md:grid-cols-2');
    const brand = screen.getByTestId('auth-brand');
    expect(brand).toHaveTextContent('Amigo Secreto');
    // Só aparece no desenho largo; no celular a marca vem do cabeçalho
    expect(brand).toHaveClass('hidden', 'md:flex');
  });
});
