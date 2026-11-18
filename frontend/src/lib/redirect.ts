/**
 * Destino seguro para o parâmetro `voltar`: só rotas internas do app (começam com uma única "/").
 * Evita open redirect para outro domínio (`//site`, `/\site`, `https://…`).
 */
export function safeRedirect(target: string | null | undefined): string {
  if (!target || !target.startsWith('/') || target.startsWith('//') || target.startsWith('/\\')) {
    return '/';
  }
  return target;
}

/** Sufixo `?voltar=…` para preservar o destino entre /entrar e /cadastro. */
export function voltarQuery(target: string | null): string {
  const safe = safeRedirect(target);
  return safe === '/' ? '' : `?voltar=${encodeURIComponent(safe)}`;
}
