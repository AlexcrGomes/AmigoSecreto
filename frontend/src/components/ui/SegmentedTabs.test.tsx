import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { SegmentedTabs } from './SegmentedTabs';

const ITEMS = [
  { value: 'resumo', label: 'Resumo' },
  { value: 'pessoas', label: 'Pessoas' },
  { value: 'desejos', label: 'Desejos' },
];

describe('SegmentedTabs', () => {
  it('é um grupo de opções com rótulo, uma opção por item e a atual marcada', () => {
    render(
      <SegmentedTabs
        label="Partes do grupo"
        items={ITEMS}
        value="pessoas"
        onValueChange={vi.fn()}
      />,
    );

    const group = screen.getByRole('radiogroup', { name: 'Partes do grupo' });
    expect(group).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: 'Pessoas' })).toHaveAttribute('aria-checked', 'true');
    expect(screen.getByRole('radio', { name: 'Resumo' })).toHaveAttribute('aria-checked', 'false');
    expect(screen.getByRole('radio', { name: 'Desejos' })).toBeInTheDocument();
  });

  it('tocar numa opção avisa a escolha', async () => {
    const onValueChange = vi.fn();
    render(
      <SegmentedTabs
        label="Partes do grupo"
        items={ITEMS}
        value="resumo"
        onValueChange={onValueChange}
      />,
    );

    await userEvent.click(screen.getByRole('radio', { name: 'Desejos' }));

    expect(onValueChange).toHaveBeenCalledWith('desejos');
  });
});
