import { useState, type SubmitEvent } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, ApiError, errorMessage, write } from '../shared/api';
import { parseAmount, today, validDate, type Booking, type BookingInput } from './model';

export function BookingPage() {
  const { id } = useParams();
  const booking = useQuery({
    queryKey: ['booking', id],
    queryFn: () => api<Booking>(`/api/bookings/${id}`),
    enabled: !!id,
  });
  if (id && booking.isPending) return <main className="page" role="status">Buchung wird geladen …</main>;
  if (id && booking.isError) {
    return (
      <main className="page">
        <p role="alert" className="error">{errorMessage(booking.error)}</p>
        <Link to="/">Zur Übersicht</Link>
      </main>
    );
  }
  return <BookingForm key={id ?? 'new'} booking={booking.data} />;
}

export function BookingForm({ booking }: { booking?: Booking }) {
  const navigate = useNavigate();
  const location = useLocation();
  const previous = (location.state as { returnTo?: string } | null)?.returnTo;
  const returnTo = previous?.startsWith('/?') ? previous : '/';
  const client = useQueryClient();
  const [draft, setDraft] = useState<BookingInput>(booking ? {
    type: booking.type, description: booking.description, amount: booking.amount, date: booking.date,
  } : {
    type: 'EXPENSE', description: '', amount: '', date: today(),
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const mutation = useMutation({
    mutationFn: (input: BookingInput) => write<Booking>(
      booking ? `/api/bookings/${booking.id}` : '/api/bookings',
      booking ? 'PUT' : 'POST', input,
    ),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: ['bookings'] });
      client.removeQueries({ queryKey: ['booking'] });
      navigate(returnTo);
    },
    onError: error => {
      if (error instanceof ApiError && error.problem.errors) {
        setErrors(Object.fromEntries(error.problem.errors.map(e => [e.field, 'Bitte prüfe dieses Feld.'])));
      }
    },
  });

  function submit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    const amount = parseAmount(draft.amount);
    const next: Record<string, string> = {};
    if (!draft.description.trim()) next.description = 'Bitte gib eine Beschreibung ein.';
    if (!amount) next.amount = 'Bitte gib einen positiven Betrag ein, z. B. 12,34.';
    if (!validDate(draft.date)) {
      next.date = 'Bitte wähle ein gültiges Datum.';
    }
    setErrors(next);
    if (Object.keys(next).length === 0) mutation.mutate({ ...draft, amount: amount! });
  }

  return (
    <main className="page form-page">
      <Link className="back-link" to={returnTo}>← Zur Übersicht</Link>
      <div className="page-heading">
        <div>
          <span className="eyebrow">DEINE BUCHUNGEN</span>
          <h1>{booking ? 'Buchung bearbeiten.' : 'Eine neue Buchung.'}</h1>
          <p>Ein kleiner Eintrag. Ein klarer Überblick.</p>
        </div>
      </div>
      <form className="booking-form" onSubmit={submit} noValidate>
        <fieldset disabled={mutation.isPending}>
          <legend>Buchungsart</legend>
          <div className="type-switch">
            {(['EXPENSE', 'INCOME'] as const).map(type => (
              <label key={type} className={draft.type === type ? 'selected' : ''}>
                <input type="radio" name="type" value={type} checked={draft.type === type} onChange={() => setDraft({ ...draft, type })} />
                {type === 'INCOME' ? '↙ Einnahme' : '↗ Ausgabe'}
              </label>
            ))}
          </div>
          <label htmlFor="description">Beschreibung</label>
          <input id="description" autoFocus value={draft.description} placeholder="Wofür war die Buchung?"
            onChange={event => setDraft({ ...draft, description: event.target.value })}
            aria-invalid={!!errors.description} aria-describedby={errors.description ? 'description-error' : undefined} />
          {errors.description && <p className="field-error" id="description-error">{errors.description}</p>}
          <div className="form-grid">
            <div>
              <label htmlFor="amount">Betrag in EUR</label>
              <input id="amount" type="text" inputMode="decimal" placeholder="0,00" value={draft.amount}
                onChange={event => setDraft({ ...draft, amount: event.target.value })}
                aria-invalid={!!errors.amount} aria-describedby={errors.amount ? 'amount-error' : 'amount-hint'} />
              {errors.amount && <p className="field-error" id="amount-error">{errors.amount}</p>}
              <small id="amount-hint">Positiver Betrag, ohne Tausendertrennzeichen.</small>
            </div>
            <div>
              <label htmlFor="date">Datum</label>
              <input id="date" type="date" value={draft.date} onChange={event => setDraft({ ...draft, date: event.target.value })}
                aria-invalid={!!errors.date} aria-describedby={errors.date ? 'date-error' : undefined} />
              {errors.date && <p className="field-error" id="date-error">{errors.date}</p>}
            </div>
          </div>
          {mutation.isError && <p role="alert" className="banner error">{errorMessage(mutation.error)}</p>}
          <div className="form-actions">
            <Link className="button" to={returnTo}>Abbrechen</Link>
            <button className="primary" type="submit" disabled={mutation.isPending}>
              {mutation.isPending ? 'Wird gespeichert …' : 'Buchung speichern'}
            </button>
          </div>
        </fieldset>
      </form>
    </main>
  );
}
