/** Tela ainda não implementada: as histórias US1–US4 substituem cada uma. */
export function PagePlaceholder({ title }: { title: string }) {
  return (
    <div className="py-6 md:py-16">
      <section className="surface-glass mx-auto max-w-xl rounded-card p-6 text-center md:p-10">
        <h1 className="text-2xl font-extrabold tracking-tight text-text">{title}</h1>
      </section>
    </div>
  );
}
