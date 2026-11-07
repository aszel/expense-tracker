import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ConfirmDialog } from '../shared/ConfirmDialog';
import { api, errorMessage, write } from '../shared/api';
import { monthRange, sortBookings, validDate, type Booking } from '../bookings/model';
import { BookingList } from './BookingList';
import { Summary } from './Summary';

export function Dashboard() {
  const [search, setSearch] = useSearchParams();
  const initial = monthRange();
  const from = search.get('from') ?? initial.from;
  const to = search.get('to') ?? initial.to;
  const returnTo = `/?${new URLSearchParams({ from, to })}`;
  const [draft, setDraft] = useState({ from, to });
  const [deleting, setDeleting] = useState<Booking>();
  const client = useQueryClient();
  const valid = validDate(from) && validDate(to) && from <= to;

  useEffect(() => { setDraft({ from, to }); }, [from, to]);
  const list = useQuery({
    queryKey: ['bookings', from, to],
    queryFn: () => api<Booking[]>(`/api/bookings?${new URLSearchParams({ from, to })}`),
    enabled: valid,
  });
  const remove = useMutation({
    mutationFn: (id: number) => write(`/api/bookings/${id}`, 'DELETE'),
    onSuccess: async () => {
      setDeleting(undefined);
      await client.invalidateQueries({ queryKey: ['bookings'] });
    },
  });
  const bookings = sortBookings(list.data ?? []);

  function changeMonth(offset: number) {
    const range = monthRange(valid ? from : undefined, offset);
    setDraft(range);
    setSearch(range);
  }

  return (
    <main className="page">
      <div className="page-heading">
        <div>
          <span className="eyebrow">DEINE FINANZEN</span>
          <h1>Alles im Überblick.</h1>
          <p>Einnahmen, Ausgaben und Raum für deine Pläne.</p>
        </div>
        <Link className="button primary" to="/bookings/new" state={{ returnTo }}>＋ Neue Buchung</Link>
      </div>
      <section className="period" aria-label="Zeitraum">
        <div className="month-nav">
          <button aria-label="Vorheriger Monat" onClick={() => changeMonth(-1)}>←</button>
          <span>Zeitraum</span>
          <button aria-label="Nächster Monat" onClick={() => changeMonth(1)}>→</button>
        </div>
        <form onSubmit={event => { event.preventDefault(); setSearch(draft); }}>
          <label>
            Von
            <input type="date" required value={draft.from} onChange={event => setDraft({ ...draft, from: event.target.value })} />
          </label>
          <span className="date-dash" aria-hidden="true">—</span>
          <label>
            Bis
            <input type="date" required min={draft.from} value={draft.to} onChange={event => setDraft({ ...draft, to: event.target.value })} />
          </label>
          <button type="submit" disabled={!draft.from || !draft.to || draft.from > draft.to}>Anwenden</button>
        </form>
      </section>
      {!valid && <p role="alert" className="error">Bitte wähle einen gültigen Zeitraum.</p>}
      {list.isPending && valid && <p role="status">Buchungen werden geladen …</p>}
      {list.isError && (
        <div className="banner error" role="alert">
          {errorMessage(list.error)} <button onClick={() => void list.refetch()}>Erneut versuchen</button>
        </div>
      )}
      {list.isSuccess && valid && (
        <>
          <Summary bookings={bookings} />
          <BookingList bookings={bookings} from={from} to={to} returnTo={returnTo} onDelete={booking => { remove.reset(); setDeleting(booking); }} />
        </>
      )}
      {deleting && (
        <ConfirmDialog busy={remove.isPending} onCancel={() => setDeleting(undefined)}>
          <h2 id="delete-title">Buchung löschen?</h2>
          <p>„{deleting.description}“ wird dauerhaft gelöscht.</p>
          {remove.isError && <p role="alert" className="error">{errorMessage(remove.error)}</p>}
          <div className="form-actions">
            <button autoFocus disabled={remove.isPending} onClick={() => setDeleting(undefined)}>Abbrechen</button>
            <button className="destructive" disabled={remove.isPending} onClick={() => remove.mutate(deleting.id)}>
              {remove.isPending ? 'Wird gelöscht …' : 'Endgültig löschen'}
            </button>
          </div>
        </ConfirmDialog>
      )}
    </main>
  );
}
