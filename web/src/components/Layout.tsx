import { useEffect, useState } from 'react';
import { Link, NavLink, Outlet, useLocation } from 'react-router';
import { useClinic } from '../hooks';
import { useT } from '../i18n';
import { brandName } from '../lib/format';
import { ChatWidget } from './ChatWidget';
import { Logo } from './Logo';

export function Layout() {
  const t = useT();
  const clinic = useClinic();
  const location = useLocation();
  const [menuOpen, setMenuOpen] = useState(false);

  useEffect(() => {
    setMenuOpen(false);
    if (!location.hash) {
      window.scrollTo({ top: 0 });
      return;
    }
    const timer = setTimeout(
      () => document.getElementById(location.hash.slice(1))?.scrollIntoView({ behavior: 'smooth' }),
      100,
    );
    return () => clearTimeout(timer);
  }, [location.pathname, location.hash]);

  const brand = clinic ? brandName(clinic.name) : '';
  const phoneHref = clinic ? 'tel:' + clinic.phone.replace(/[^\d+]/g, '') : undefined;

  return (
    <div className="page">
      <header className="header">
        <div className="container header__inner">
          <Link to="/" className="brand" aria-label="На главную">
            <Logo />
            <span className="brand__text">
              <span className="brand__name">{brand || '\u00a0'}</span>
              <span className="brand__tag">стоматология</span>
            </span>
          </Link>
          <button
            className="header__burger"
            aria-label="Меню"
            aria-expanded={menuOpen}
            onClick={() => setMenuOpen((v) => !v)}
          >
            <span />
            <span />
          </button>
          <nav className={'nav' + (menuOpen ? ' nav--open' : '')}>
            <NavLink to="/services">{t.nav.services}</NavLink>
            <NavLink to="/doctors">{t.nav.doctors}</NavLink>
            <Link to="/#contacts">{t.nav.contacts}</Link>
            {clinic && (
              <a className="nav__phone" href={phoneHref}>
                {clinic.phone}
              </a>
            )}
            <Link to="/booking" className="btn btn--primary btn--sm">
              {t.nav.book}
            </Link>
          </nav>
        </div>
      </header>

      <main className="main">
        <Outlet />
      </main>

      <footer className="footer">
        <div className="container footer__inner">
          <div>
            <div className="footer__brand">{clinic?.name}</div>
            <div className="footer__muted">{clinic?.address}</div>
          </div>
          <div className="footer__links">
            {clinic && <a href={phoneHref}>{clinic.phone}</a>}
            {clinic?.email && <a href={'mailto:' + clinic.email}>{clinic.email}</a>}
            <Link to="/privacy">{t.footer.privacy}</Link>
          </div>
          <div className="footer__muted footer__small">
            © {new Date().getFullYear()} {brand && `${brand}. `}
            {t.footer.rights}
          </div>
        </div>
      </footer>
      <ChatWidget />
    </div>
  );
}
