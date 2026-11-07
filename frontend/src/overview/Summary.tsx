import { money, totals, type Booking } from '../bookings/model';

export function Summary({ bookings }: { bookings: Booking[] }) {
  const sums = totals(bookings);
  return (
    <section className="summary" aria-label="Zusammenfassung">
      <article>
        <span className="metric-icon income" aria-hidden="true">↙</span>
        <p>Einnahmen</p>
        <strong data-testid="income">{money(sums.income)}</strong>
        <small>Im gewählten Zeitraum</small>
      </article>
      <article>
        <span className="metric-icon expense" aria-hidden="true">↗</span>
        <p>Ausgaben</p>
        <strong data-testid="expense">{money(sums.expense)}</strong>
        <small>Im gewählten Zeitraum</small>
      </article>
      <article className="balance">
        <span className="metric-icon" aria-hidden="true">＝</span>
        <p>Differenz</p>
        <strong data-testid="difference">{money(sums.difference)}</strong>
        <small>Einnahmen minus Ausgaben · kein Kontostand</small>
      </article>
    </section>
  );
}
