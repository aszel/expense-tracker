import { Link } from 'react-router-dom';
import { dateLabel, money, type Booking } from '../bookings/model';

interface Props {
  bookings: Booking[];
  from: string;
  to: string;
  returnTo: string;
  onDelete: (booking: Booking) => void;
}

export function BookingList({ bookings, from, to, returnTo, onDelete }: Props) {
  return (
    <section className="ledger">
      <div className="section-heading">
        <div>
          <h2>Deine Buchungen</h2>
          <p>{dateLabel(from)} – {dateLabel(to)}</p>
        </div>
        <span className="count">
          {bookings.length} {bookings.length === 1 ? 'Buchung' : 'Buchungen'}
        </span>
      </div>
      {bookings.length === 0 ? (
        <div className="empty">
          <span aria-hidden="true">＋</span>
          <h3>Noch ganz übersichtlich.</h3>
          <p>In diesem Zeitraum gibt es keine Buchungen.</p>
          <Link to="/bookings/new" state={{ returnTo }}>Erste Buchung erfassen →</Link>
        </div>
      ) : (
        <>
          <div className="ledger-head" aria-hidden="true">
            <span>Buchung</span><span>Datum</span><span>Betrag</span><span>Aktionen</span>
          </div>
          <ul className="booking-list">
            {bookings.map(booking => (
              <li key={booking.id} className="booking-row">
                <div className="booking-description">
                  <span aria-hidden="true" className={`entry-icon ${booking.type === 'INCOME' ? 'income' : 'expense'}`}>
                    {booking.type === 'INCOME' ? '↙' : '↗'}
                  </span>
                  <div>
                    <strong>{booking.description}</strong>
                    <small>{booking.type === 'INCOME' ? 'Einnahme' : 'Ausgabe'}</small>
                  </div>
                </div>
                <time dateTime={booking.date}>{dateLabel(booking.date)}</time>
                <strong className={`entry-amount ${booking.type === 'INCOME' ? 'income-text' : ''}`}>
                  {booking.type === 'INCOME' ? '+' : '−'}{money(booking.amount)}
                </strong>
                <div className="row-actions">
                  <Link to={`/bookings/${booking.id}/edit`} state={{ returnTo }} aria-label={`${booking.description} bearbeiten`}>
                    Bearbeiten
                  </Link>
                  <button className="quiet danger" aria-label={`${booking.description} löschen`} onClick={() => onDelete(booking)}>
                    Löschen
                  </button>
                </div>
              </li>
            ))}
          </ul>
        </>
      )}
    </section>
  );
}
