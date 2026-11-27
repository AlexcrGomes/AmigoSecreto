import { useId, type InputHTMLAttributes, type Ref } from 'react';
import { IconAlert } from './icons';

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  /** Mensagem de erro do campo (marca aria-invalid). */
  error?: string;
  /** Dica sempre visível (ex.: regra de senha). */
  hint?: string;
  ref?: Ref<HTMLInputElement>;
}

/**
 * Campo de texto nativo com o vidro da Glisse (research R46): o `Input` da Glisse é sempre
 * controlado e não funciona com o `register()` do react-hook-form, então usamos só as classes dela.
 */
export function Input({ label, error, hint, id, className = '', ref, ...rest }: InputProps) {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  const hintId = `${inputId}-dica`;
  const errorId = `${inputId}-erro`;
  const describedBy = [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(' ');

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={inputId} className="text-sm font-semibold text-text">
        {label}
      </label>
      <input
        ref={ref}
        id={inputId}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy || undefined}
        className={`lg-glass lg-input field-input min-h-11 w-full ${className}`.trim()}
        {...rest}
      />
      {hint && (
        <p id={hintId} className="text-sm text-text-muted">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="flex items-start gap-1.5 text-sm font-medium text-danger">
          <IconAlert className="mt-px size-4 shrink-0" />
          {error}
        </p>
      )}
    </div>
  );
}
