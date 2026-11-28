import { TabBar } from 'glisse';
import {
  createContext,
  forwardRef,
  useContext,
  type AnchorHTMLAttributes,
  type ReactNode,
} from 'react';
import { Link } from 'react-router';

export interface BottomTabItem {
  value: string;
  label: ReactNode;
  icon: ReactNode;
  /** Destino; sem destino, o item é uma ação (ex.: "Sair") e chama `onAction`. */
  to?: string;
}

const ACTION_PREFIX = '#acao-';

const ActionContext = createContext<(value: string) => void>(() => {});

/**
 * Adaptador da Glisse para o React Router (research R49): itens com destino viram `<Link>`; itens de
 * ação viram botões, para a barra continuar sendo uma navegação (`role="navigation"`).
 */
const TabLink = forwardRef<
  HTMLAnchorElement & HTMLButtonElement,
  AnchorHTMLAttributes<HTMLElement>
>(function TabLink({ href = '/', children, ...rest }, ref) {
  const onAction = useContext(ActionContext);
  if (href.startsWith(ACTION_PREFIX)) {
    const { className, draggable } = rest;
    return (
      <button
        ref={ref}
        type="button"
        className={className}
        draggable={draggable}
        onClick={() => onAction(href.slice(ACTION_PREFIX.length))}
      >
        {children}
      </button>
    );
  }
  return (
    <Link ref={ref} to={href} {...rest}>
      {children}
    </Link>
  );
});

interface BottomTabBarProps {
  label: string;
  items: BottomTabItem[];
  /** Item marcado, sempre calculado pela rota (a barra é controlada). */
  value: string | null;
  onAction: (value: string) => void;
}

/** Barra de abas de vidro da Glisse, controlada pela rota. Só no celular. */
export function BottomTabBar({ label, items, value, onAction }: BottomTabBarProps) {
  return (
    <ActionContext.Provider value={onAction}>
      <TabBar
        aria-label={label}
        className="bottom-tab-bar"
        link={TabLink}
        value={value}
        // A marcação vem só da rota: tocar não muda a aba marcada por conta própria
        onValueChange={() => {}}
        items={items.map((item) => ({
          value: item.value,
          label: item.label,
          icon: item.icon,
          href: item.to ?? `${ACTION_PREFIX}${item.value}`,
        }))}
      />
    </ActionContext.Provider>
  );
}
