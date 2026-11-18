import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterAll, afterEach, beforeAll, vi } from 'vitest';
import { FakeEventSource } from './eventSource';
import { server } from './server';

// Qualquer requisição sem handler definido no teste é um erro: nada de chamadas reais.
beforeAll(() => {
  server.listen({ onUnhandledFrame: 'error' });
  vi.stubGlobal('EventSource', FakeEventSource);
});
afterEach(() => {
  server.resetHandlers();
  cleanup();
  FakeEventSource.reset();
});
afterAll(() => {
  server.close();
  vi.unstubAllGlobals();
});
