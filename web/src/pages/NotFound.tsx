import { Link } from 'react-router';
import { useTitle } from '../hooks';
import { useT } from '../i18n';

export function NotFound() {
  const t = useT();
  useTitle(t.notFound.title, undefined);

  return (
    <div className="container page-body narrow not-found">
      <div className="not-found__code" aria-hidden="true">
        4<span>0</span>4
      </div>
      <h1 className="page-title">{t.notFound.title}</h1>
      <p className="page-lead">{t.notFound.text}</p>
      <div className="hero__actions">
        <Link to="/" className="btn btn--ghost">
          {t.notFound.home}
        </Link>
        <Link to="/booking" className="btn btn--primary">
          {t.nav.book}
        </Link>
      </div>
    </div>
  );
}
