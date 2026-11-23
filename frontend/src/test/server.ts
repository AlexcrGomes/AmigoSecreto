import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';

// Servidor MSW compartilhado. Cada teste registra os handlers de que precisa com server.use(...).
// Padrão (sobrevive ao resetHandlers): ninguém tem avisos de pedido de saída.
export const server = setupServer(
  http.get('/api/v1/exit-request-notices', () => HttpResponse.json([])),
);
