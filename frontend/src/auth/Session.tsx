import { useEffect, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Outlet } from 'react-router-dom';
import { api, csrf, errorMessage } from '../shared/api';

export function Session() {
  const client = useQueryClient();
  const [expired, setExpired] = useState(false);
  const [logoutError, setLogoutError] = useState('');
  const [loggingOut, setLoggingOut] = useState(false);

  useEffect(() => {
    const expire = () => {
      setExpired(true);
      void client.cancelQueries();
      client.clear();
    };
    window.addEventListener('session-expired', expire);
    return () => window.removeEventListener('session-expired', expire);
  }, [client]);

  const me = useQuery({
    queryKey: ['me'],
    queryFn: () => api<{ id: string; username: string }>('/api/me'),
    enabled: !expired,
  });

  async function logout() {
    setLogoutError('');
    setLoggingOut(true);
    try {
      const token = await csrf();
      const form = document.createElement('form');
      form.method = 'POST';
      form.action = '/logout';
      const input = document.createElement('input');
      input.type = 'hidden';
      input.name = '_csrf';
      input.value = token.token;
      form.append(input);
      document.body.append(form);
      form.submit();
    } catch (error) {
      setLogoutError(errorMessage(error));
      setLoggingOut(false);
    }
  }

  if (expired) {
    return (
      <main className="session-card">
        <span className="eyebrow">KONTUR</span>
        <h1>Bitte melde dich an.</h1>
        <p>Deine Sitzung ist abgelaufen. Nicht gespeicherte Änderungen wurden nicht übertragen.</p>
        <a className="button primary" href="/login">Mit Keycloak anmelden</a>
      </main>
    );
  }
  if (me.isPending) return <main className="session-card" role="status">Anmeldung wird geprüft …</main>;
  if (me.isError) {
    return (
      <main className="session-card">
        <h1>Anmeldung prüfen</h1>
        <p role="alert">{errorMessage(me.error)}</p>
        <button onClick={() => void me.refetch()}>Erneut versuchen</button> <a href="/login">Anmelden</a>
      </main>
    );
  }
  return (
    <>
      <header className="topbar">
        <a className="brand" href="/" aria-label="Kontur Startseite">
          <span className="brand-icon">k.</span> kontur
          <span className="brand-tag">FINANZEN IM BLICK</span>
        </a>
        <div className="account">
          <span>{me.data.username || 'Mein Konto'}</span>
          <button className="quiet" onClick={() => void logout()} disabled={loggingOut}>
            {loggingOut ? 'Abmelden …' : 'Abmelden ↗'}
          </button>
        </div>
      </header>
      {logoutError && <p className="banner error" role="alert">{logoutError}</p>}
      <Outlet />
      <footer>Dein persönlicher Überblick · Alle Beträge in EUR</footer>
    </>
  );
}
