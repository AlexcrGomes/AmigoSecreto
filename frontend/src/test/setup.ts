import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterAll, afterEach, beforeAll, vi } from 'vitest';
import { FakeEventSource } from './eventSource';
import { installMatchMedia, resetMedia } from './media';
import { server } from './server';

// O jsdom não tem o que a Glisse usa para desenhar o vidro (feature 005, research R53). Sem canvas,
// ela cai no modo sem lente; os testes continuam consultando por papel e nome acessível.
installMatchMedia();
HTMLCanvasElement.prototype.getContext = (() => null) as unknown as HTMLCanvasElement['getContext'];
if (typeof window.ResizeObserver === 'undefined') {
  window.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  } as unknown as typeof ResizeObserver;
}
if (typeof (document as { fonts?: unknown }).fonts === 'undefined') {
  Object.defineProperty(document, 'fonts', {
    value: { ready: Promise.resolve(), addEventListener() {}, removeEventListener() {} },
  });
}
if (!Element.prototype.setPointerCapture) {
  Element.prototype.setPointerCapture = () => {};
  Element.prototype.releasePointerCapture = () => {};
  Element.prototype.hasPointerCapture = () => false;
}

// Qualquer requisição sem handler definido no teste é um erro: nada de chamadas reais.
beforeAll(() => {
  server.listen({ onUnhandledFrame: 'error' });
  vi.stubGlobal('EventSource', FakeEventSource);
});
afterEach(() => {
  server.resetHandlers();
  cleanup();
  FakeEventSource.reset();
  resetMedia();
});
afterAll(() => {
  server.close();
  vi.unstubAllGlobals();
});
