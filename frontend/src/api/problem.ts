/** Erro de um campo na validação do backend. */
export interface FieldError {
  field: string;
  message: string;
}

/** Corpo de erro da API (RFC 9457, schema `Problem` de contracts/openapi.yaml). */
export interface Problem {
  type?: string;
  title?: string;
  status?: number;
  /** Mensagem em pt-BR para exibir ao usuário. */
  detail: string;
  errors?: FieldError[];
}
