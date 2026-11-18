import { Button } from '../ui/Button';
import { Card } from '../ui/Card';
import { useToast } from '../ui/Toast';

interface ShareCodeProps {
  /** Formatado `XXXX-XXXX`. */
  code: string;
  groupName: string;
}

/** Código do grupo em destaque, com "Copiar" e "Compartilhar" (Web Share API, ou copia o link). */
export function ShareCode({ code, groupName }: ShareCodeProps) {
  const toast = useToast();
  const link = `${window.location.origin}/entrar-no-grupo?codigo=${encodeURIComponent(code)}`;

  async function copy(text: string, success: string) {
    try {
      await navigator.clipboard.writeText(text);
      toast.show(success, 'success');
    } catch {
      toast.show('Não foi possível copiar. Selecione o código e copie à mão.', 'error');
    }
  }

  async function share() {
    if (typeof navigator.share !== 'function') {
      await copy(link, 'Link copiado!');
      return;
    }
    try {
      await navigator.share({
        title: `Amigo secreto: ${groupName}`,
        text: `Entre no amigo secreto "${groupName}" com o código ${code}`,
        url: link,
      });
    } catch (error) {
      // Quem fechou a janela de compartilhar não quer aviso
      if (error instanceof DOMException && error.name === 'AbortError') return;
      await copy(link, 'Link copiado!');
    }
  }

  return (
    <Card className="text-center">
      <p className="text-sm font-medium text-text-muted">Código do grupo</p>
      <p className="mt-1 font-mono text-3xl font-bold tracking-widest text-text select-all">
        {code}
      </p>
      <p className="mt-2 text-sm text-text-muted">
        Compartilhe com quem vai participar do sorteio.
      </p>
      <div className="mt-4 grid grid-cols-2 gap-3">
        <Button variant="secondary" onClick={() => void copy(code, 'Código copiado!')}>
          Copiar
        </Button>
        <Button onClick={() => void share()}>Compartilhar</Button>
      </div>
    </Card>
  );
}
