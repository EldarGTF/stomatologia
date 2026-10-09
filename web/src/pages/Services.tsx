import { useState } from 'react';
import { Link } from 'react-router';
import { api } from '../api';
import { ErrorNote, Loading } from '../components/States';
import { useAsync, useClinic, useTitle } from '../hooks';
import { useT } from '../i18n';
import { brandName, money } from '../lib/format';

export function Services() {
  const t = useT();
  const clinic = useClinic();
  useTitle(t.services.title, clinic ? brandName(clinic.name) : undefined);
  const services = useAsync(api.services, []);
  const [query, setQuery] = useState('');

  const q = query.trim().toLowerCase();
  const list = (services.data ?? []).filter(
    (s) => !q || s.name.toLowerCase().includes(q) || s.description?.toLowerCase().includes(q),
  );

  return (
    <div className="container page-body">
      <header className="page-head">
        <h1 className="page-title">{t.services.title}</h1>
        <p className="page-lead">{t.services.lead}</p>
        <input
          className="input input--search"
          type="search"
          placeholder={t.services.search}
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          aria-label={t.services.search}
        />
      </header>

      {services.loading && <Loading />}
      {services.error && <ErrorNote message={services.error} onRetry={services.reload} />}
      {services.data && list.length === 0 && <p className="muted">{t.services.empty}</p>}

      {list.length > 0 && (
        <ul className="price-list">
          {list.map((s) => (
            <li key={s.id} className="price-list__item">
              <div className="price-list__info">
                <span className="price-list__name">{s.name}</span>
                {s.description && <span className="price-list__desc">{s.description}</span>}
                <span className="price-list__meta">
                  {s.durationMinutes} {t.common.minutes}
                </span>
              </div>
              <span className="price-list__price">{money(s.price)}</span>
              <Link to={`/booking?service=${s.id}`} className="btn btn--ghost btn--sm">
                {t.services.book}
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
