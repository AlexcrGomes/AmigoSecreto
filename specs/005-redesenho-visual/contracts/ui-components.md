# Contrato: componentes de interface — feature 005

Os componentes de `frontend/src/components/ui/` são a **única** porta para a Glisse (research R43,
R46). Páginas e `components/group/*` continuam importando só deles. A API (props) de cada
componente existente **não muda**, para as telas e os testes de comportamento continuarem
valendo; o que muda é a aparência.

| Componente | API (mantida) | Por baixo | Regras |
|------------|---------------|-----------|--------|
| `Button` | `variant` (`primary`, `secondary`, `ghost`, `danger`), `loading`, `fullWidth`, atributos de `<button>` | `glisse` `Button`: `primary` → `filled`; `secondary`/`ghost` → `glass`; `danger` → `glass` + `destructive` | 44px; `loading` mostra `Spinner`, `aria-busy` e desabilita; `type="button"` por padrão |
| `buttonClasses(variant, fullWidth)` | igual | classes do projeto com os tokens | para `<Link>` com cara de botão; mesma forma e cores do `Button` |
| `Input` | `label`, `error`, `hint`, atributos de `<input>`, compatível com `register()` | `<input>` nativo com as classes `lg-glass lg-input` da Glisse, em todos os tipos (research R46: o `Input` da Glisse é controlado e não funciona com `register()`) | rótulo sempre visível; erro abaixo com ícone e `aria-describedby`; `aria-invalid`; valores iniciais e `reset()` aparecem no campo |
| `Card` | `children`, `className` | vidro fosco do projeto | `Card` com `highlight` usa `glisse` `Glass` (amigo secreto, confirmação) |
| `Modal` | `open`, `title`, `onClose`, `footer`, `children` | `glisse` `Glass` sobre um véu | `role="dialog"`, `aria-modal`, foco preso, `Esc` fecha, foco volta ao gatilho |
| `ConfirmModal` | igual | `Modal` | loading e erro dentro da janela |
| `Toast` | `useToast().show(text, tone)` | do projeto, vidro | `role="status"`; tom `error` com ícone de alerta |
| `Badge` | `tone` (`neutral`, `success`, `primary`), `children` | do projeto | pílula; `success` em dourado (R55); cor + texto (nunca só cor) |
| `Spinner` | `label`, `className` | do projeto | `role="status"`; sem animação com `prefers-reduced-motion` |
| `icons.tsx` (novo) | `IconGroups`, `IconPlus`, `IconTicket`, `IconLogout`, `IconAlert`, `IconCheck` | SVG próprio | `aria-hidden`, `currentColor` |
| `AppBackground` (novo) | — | CSS | fixo atrás de tudo, `data-glisse-backdrop`, sem conteúdo acessível; branco com brilhos vermelho e dourado (R55) |
| `Notice` (novo, US2) | `tone` (`success`, `info`, `danger`), `role`, `children` | do projeto, vidro | ícone + texto; sucesso dourado (R55) |
| `SegmentedTabs` (novo, revisão) | `label`, `items {value,label}`, `value`, `onValueChange` | `glisse` `SegmentedControl` | papel `radiogroup` com rótulo; só no celular (research R57) |
| `AppNavigation` (novo) | — | celular: `glisse` `TabBar`; ≥ 768px: menu lateral do projeto; `ConfirmModal` para "Sair" | research R49, R58 e [ui-routes.md](./ui-routes.md) |
| `AuthLayout` (novo, revisão) | `title`, `children` | do projeto | celular: marca acima + cartão; ≥ 768px: duas metades (research R56) |
