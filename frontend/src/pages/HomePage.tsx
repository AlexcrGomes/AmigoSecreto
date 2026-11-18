import { Card } from '../components/ui/Card';

/** / — "Meus grupos". Estado vazio provisório: a lista de grupos chega na US2. */
export function HomePage() {
  return (
    <div className="py-6">
      <h1 className="text-2xl font-bold text-text">Meus grupos</h1>
      <Card className="mt-6 text-center">
        <p className="text-text-muted">Você ainda não participa de nenhum grupo.</p>
      </Card>
    </div>
  );
}
