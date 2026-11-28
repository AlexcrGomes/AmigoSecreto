import { SegmentedControl } from 'glisse';

export interface SegmentedTab {
  value: string;
  label: string;
}

interface SegmentedTabsProps {
  /** Nome acessível do grupo de opções. */
  label: string;
  items: SegmentedTab[];
  value: string;
  onValueChange: (value: string) => void;
}

/**
 * Seletor de partes de uma tela (research R57): o `SegmentedControl` da Glisse, ocupando a largura
 * toda, com opções de pelo menos 44px de altura.
 */
export function SegmentedTabs({ label, items, value, onValueChange }: SegmentedTabsProps) {
  return (
    <SegmentedControl
      aria-label={label}
      className="segmented-tabs"
      items={items}
      value={value}
      onValueChange={onValueChange}
    />
  );
}
