/**
 * Fundo festivo (data-model.md §2): degradê suave com manchas de luz vermelha, verde e dourada.
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
      <span className="app-spot app-spot-red" />
      <span className="app-spot app-spot-green" />
      <span className="app-spot app-spot-gold" />
    </div>
  );
}
