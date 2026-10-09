import { Link } from 'react-router';
import { api } from '../api';
import { DoctorAvatar, ErrorNote, Loading } from '../components/States';
import { useAsync, useClinic, useTitle } from '../hooks';
import { useT } from '../i18n';
import { brandName } from '../lib/format';

export function Doctors() {
  const t = useT();
  const clinic = useClinic();
  useTitle(t.doctors.title, clinic ? brandName(clinic.name) : undefined);
  const doctors = useAsync(api.doctors, []);

  return (
    <div className="container page-body">
      <header className="page-head">
        <h1 className="page-title">{t.doctors.title}</h1>
        <p className="page-lead">{t.doctors.lead}</p>
      </header>

      {doctors.loading && <Loading />}
      {doctors.error && <ErrorNote message={doctors.error} onRetry={doctors.reload} />}

      <div className="grid grid--2">
        {(doctors.data ?? []).map((d) => (
          <article key={d.id} className="card card--doctor-wide">
            <DoctorAvatar name={d.fullName} size={72} />
            <div className="card__body">
              <h2 className="card__title card__title--lg">{d.fullName}</h2>
              <div className="card__meta">{d.specialtyName}</div>
              <div className="card__meta">
                {t.common.room} {d.roomNumber}
              </div>
            </div>
            <Link to={`/booking?doctor=${d.id}`} className="btn btn--primary btn--sm">
              {t.doctors.book}
            </Link>
          </article>
        ))}
      </div>
    </div>
  );
}
