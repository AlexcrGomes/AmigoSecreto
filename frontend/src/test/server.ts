import { setupServer } from 'msw/node';

// Servidor MSW compartilhado. Cada teste registra os handlers de que precisa com server.use(...).
export const server = setupServer();
