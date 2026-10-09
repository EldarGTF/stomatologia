import { useClinic, useTitle } from '../hooks';
import { useT } from '../i18n';
import { brandName } from '../lib/format';

export function Privacy() {
  const t = useT();
  const clinic = useClinic();
  useTitle(t.privacy.title, clinic ? brandName(clinic.name) : undefined);

  return (
    <div className="container page-body narrow prose">
      <h1 className="page-title">{t.privacy.title}</h1>
      {clinic && (
        <p className="muted">
          {clinic.name}, {clinic.address}, {clinic.phone}
        </p>
      )}
      {t.privacy.sections.map((s) => (
        <section key={s.title}>
          <h2>{s.title}</h2>
          <p>{s.text}</p>
        </section>
      ))}
    </div>
  );
}
