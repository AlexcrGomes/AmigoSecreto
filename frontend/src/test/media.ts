/**
 * `matchMedia` falso para os testes (o jsdom não tem): por padrão nenhuma consulta casa — celular,
 * tema claro, sem reduzir movimento nem transparência. `setMediaMatcher` muda o resultado e avisa os
 * ouvintes, como quando o usuário gira o tablet ou troca o modo do aparelho.
 */
type Listener = (event: MediaQueryListEvent) => void;

let matcher: (query: string) => boolean = () => false;
const lists = new Set<FakeMediaQueryList>();

class FakeMediaQueryList {
  readonly media: string;
  onchange: Listener | null = null;
  private readonly listeners = new Set<Listener>();

  constructor(media: string) {
    this.media = media;
  }

  get matches(): boolean {
    return matcher(this.media);
  }

  addEventListener(_type: string, listener: Listener) {
    this.listeners.add(listener);
  }

  removeEventListener(_type: string, listener: Listener) {
    this.listeners.delete(listener);
  }

  addListener(listener: Listener) {
    this.listeners.add(listener);
  }

  removeListener(listener: Listener) {
    this.listeners.delete(listener);
  }

  dispatchEvent(): boolean {
    return true;
  }

  notify() {
    const event = { matches: this.matches, media: this.media } as MediaQueryListEvent;
    this.listeners.forEach((listener) => listener(event));
    this.onchange?.(event);
  }
}

export function installMatchMedia() {
  window.matchMedia = (query: string) => {
    const list = new FakeMediaQueryList(query);
    lists.add(list);
    return list as unknown as MediaQueryList;
  };
}

export function setMediaMatcher(fn: (query: string) => boolean) {
  matcher = fn;
  lists.forEach((list) => list.notify());
}

export function resetMedia() {
  matcher = () => false;
  lists.clear();
}
