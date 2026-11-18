interface SpinnerProps {
  /** Com rótulo, anuncia o carregamento (role="status"); sem rótulo, é só decorativo. */
  label?: string;
  className?: string;
}

export function Spinner({ label, className = '' }: SpinnerProps) {
  const circle = (
    <span
      data-testid="spinner"
      aria-hidden="true"
      className={`inline-block size-5 animate-spin rounded-full border-2 border-current border-t-transparent ${className}`}
    />
  );
  if (!label) return circle;
  return (
    <span role="status" aria-label={label} className="inline-flex items-center">
      {circle}
    </span>
  );
}
