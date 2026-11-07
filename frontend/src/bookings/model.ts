import Decimal from 'decimal.js';

export interface Booking {
  id: number;
  type: 'INCOME' | 'EXPENSE';
  description: string;
  amount: string;
  date: string;
}
export type BookingInput = Omit<Booking, 'id'>;

export function parseAmount(input: string): string | undefined {
  const normalized = input.trim().replace(',', '.');
  return /^\d+(\.\d+)?$/.test(normalized) && new Decimal(normalized).gt(0) ? normalized : undefined;
}

export function totals(bookings: Booking[]) {
  let integer = 1;
  let fraction = 0;
  for (const booking of bookings) {
    const [whole, decimals = ''] = booking.amount.split('.');
    integer = Math.max(integer, whole.length);
    fraction = Math.max(fraction, decimals.length);
  }
  // Include every digit plus carry digits for the entire list. Decimal's default
  // 20 significant digits would silently round large amounts or long fractions.
  const Exact = Decimal.clone({ precision: integer + fraction + String(bookings.length).length + 2 });
  let income = new Exact(0);
  let expense = new Exact(0);
  for (const booking of bookings) {
    if (booking.type === 'INCOME') income = income.plus(booking.amount);
    else expense = expense.plus(booking.amount);
  }
  return { income: income.toFixed(), expense: expense.toFixed(), difference: income.minus(expense).toFixed() };
}

export function money(amount: string) {
  const [integer, fraction = ''] = new Decimal(amount).toFixed().split('.');
  return `${integer.replace(/\B(?=(\d{3})+(?!\d))/g, '.')},${fraction.padEnd(2, '0')} €`;
}

export function today() {
  const date = new Date();
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

export function validDate(date: string) {
  return /^\d{4}-\d{2}-\d{2}$/.test(date)
    && Number.isFinite(Date.parse(date))
    && new Date(date).toISOString().slice(0, 10) === date;
}

export function monthRange(date = today(), offset = 0) {
  const [year, month] = date.split('-').map(Number);
  const start = new Date(0);
  start.setFullYear(year, month - 1 + offset, 1);
  const end = new Date(start);
  end.setMonth(start.getMonth() + 1, 0);
  const prefix = `${String(start.getFullYear()).padStart(4, '0')}-${String(start.getMonth() + 1).padStart(2, '0')}`;
  return { from: `${prefix}-01`, to: `${prefix}-${String(end.getDate()).padStart(2, '0')}` };
}

export function dateLabel(date: string) { return date.split('-').reverse().join('.'); }

export function sortBookings(bookings: Booking[]) {
  return [...bookings].sort((a, b) => b.date.localeCompare(a.date) || b.id - a.id);
}
