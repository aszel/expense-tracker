import { describe, expect, it } from 'vitest';
import { money, monthRange, parseAmount, sortBookings, totals, type Booking } from './model';
const booking = (amount: string, type: Booking['type'] = 'INCOME'): Booking => ({id: 1, amount, type, description: 'Test', date: '2026-11-03'});
describe('exact money and booking logic', () => {
  it('sums beyond binary float and the default decimal precision without rounding', () => {
    expect(totals([booking('12345678901234567890.123456789'), booking('0.000000001'), booking('0.1', 'EXPENSE')]))
      .toEqual({income: '12345678901234567890.12345679', expense: '0.1', difference: '12345678901234567890.02345679'});
    expect(totals([booking('0.1'), booking('0.2')]).income).toBe('0.3');
    expect(money('123456789.123456789')).toBe('123.456.789,123456789 €');
  });
  it('accepts German decimal input, never thousands separators, negatives or exponents', () => {
    expect(parseAmount('12,34567')).toBe('12.34567');
    for (const invalid of ['0', '-1', '1.234,56', '1e3', '', 'NaN']) expect(parseAmount(invalid)).toBeUndefined();
  });
  it('handles leap years and month boundaries and sorts newest first', () => {
    expect(monthRange('2024-02-15')).toEqual({from:'2024-02-01',to:'2024-02-29'});
    expect(monthRange('2026-01-01',-1)).toEqual({from:'2025-12-01',to:'2025-12-31'});
    const a = booking('1'), b = {...a,id:2}, c = {...a,id:3,date:'2026-11-02'};
    expect(sortBookings([a,c,b]).map(b => b.id)).toEqual([2,1,3]);
  });
});
