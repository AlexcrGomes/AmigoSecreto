/**
 * Fundo (data-model.md §2): branco quente com brilhos suaves de vermelho e dourado (revisão R55).
 * Fica fixo atrás de tudo, é só decoração e é o que a Glisse refrata no Safari
 * (`data-glisse-backdrop`). Texto nunca fica direto sobre ele (research R48).
 */
export function AppBackground() {
  return (
    <div
      data-testid="app-background"
      data-glisse-backdrop=""
      aria-hidden="true"
      className="app-background pointer-events-none fixed inset-0 -z-10 overflow-hidden"
    >
      <span className="app-spot app-spot-gold app-spot-top" />
      <span className="app-spot app-spot-red" />
      <span className="app-spot app-spot-gold app-spot-bottom" />
    </div>
  );
}
