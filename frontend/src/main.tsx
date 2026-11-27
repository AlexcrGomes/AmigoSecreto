import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App';
// O CSS da Glisse antes do nosso: os tokens do app sobrescrevem as variáveis dela (feature 005, R44/R45)
import 'glisse/styles.css';
import './index.css';

const root = document.getElementById('root');
if (root) {
  createRoot(root).render(
    <StrictMode>
      <App />
    </StrictMode>,
  );
}
