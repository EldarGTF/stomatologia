import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import { api, type ClinicInfo } from './api';

export interface AsyncState<T> {
  data: T | null;
  error: string | null;
  loading: boolean;
  reload: () => void;
}

/** Загрузка данных с повтором; deps — когда перезагружать (например, выбранная услуга). */
export function useAsync<T>(load: () => Promise<T>, deps: unknown[]): AsyncState<T> {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    load()
      .then((result) => !cancelled && setData(result))
      .catch((e: Error) => !cancelled && setError(e.message))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [...deps, attempt]);

  const reload = useCallback(() => setAttempt((n) => n + 1), []);
  return { data, error, loading, reload };
}

const ClinicContext = createContext<ClinicInfo | null>(null);

/** Реквизиты клиники из «Настроек» CRM — название, адрес, телефон — нужны на каждой странице. */
export function ClinicProvider({ children }: { children: ReactNode }) {
  const [clinic, setClinic] = useState<ClinicInfo | null>(null);
  useEffect(() => {
    api.clinic().then(setClinic).catch(() => setClinic(null));
  }, []);
  return <ClinicContext.Provider value={clinic}>{children}</ClinicContext.Provider>;
}

export const useClinic = () => useContext(ClinicContext);

/** Заголовок вкладки браузера: «Услуги и цены — Eldar». */
export function useTitle(title: string, brand: string | undefined) {
  useEffect(() => {
    document.title = brand ? `${title} — ${brand}` : title;
  }, [title, brand]);
}
