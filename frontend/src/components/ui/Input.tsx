import { useId, type InputHTMLAttributes, type Ref } from 'react';

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  /** Mensagem de erro do campo (marca aria-invalid). */
  error?: string;
  /** Dica sempre visível (ex.: regra de senha). */
  hint?: string;
  ref?: Ref<HTMLInputElement>;
}

export function Input({ label, error, hint, id, className = '', ref, ...rest }: InputProps) {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  const hintId = `${inputId}-dica`;
  const errorId = `${inputId}-erro`;
  const describedBy = [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(' ');

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={inputId} className="text-sm font-medium text-text">
        {label}
      </label>
      <input
        ref={ref}
        id={inputId}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy || undefined}
        className={[
          'min-h-11 w-full rounded-control border bg-surface px-3 text-base text-text',
          'focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-primary',
          error ? 'border-danger' : 'border-border',
          className,
        ].join(' ')}
        {...rest}
      />
      {hint && (
        <p id={hintId} className="text-sm text-text-muted">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="text-sm text-danger">
          {error}
        </p>
      )}
    </div>
  );
}
