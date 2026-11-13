import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import './index.css';

// Ponto de entrada provisório da fase de Setup: a T032 substitui por App, rotas e providers.
const root = document.getElementById('root');
if (root) {
  createRoot(root).render(
    <StrictMode>
      <main className="mx-auto max-w-md px-4 py-10">
        <h1 className="text-2xl font-bold text-primary">Amigo Secreto</h1>
      </main>
    </StrictMode>,
  );
}
