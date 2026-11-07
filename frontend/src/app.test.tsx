import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { BookingForm } from './bookings/BookingForm';
import { Dashboard } from './overview/Dashboard';
import { Session } from './auth/Session';

const fetchMock = vi.fn<typeof fetch>();
const ok = (data: unknown, status=200) => new Response(JSON.stringify(data), {status, headers:{'Content-Type':'application/json'}});
let client: QueryClient;
function view(element: React.ReactNode) {
  return render(<QueryClientProvider client={client}><MemoryRouter>{element}</MemoryRouter></QueryClientProvider>);
}
beforeEach(() => {
  client = new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false}}});
  vi.stubGlobal('fetch', fetchMock); fetchMock.mockReset();
});
afterEach(() => { client.clear(); vi.unstubAllGlobals(); });
describe('forms and API failures', () => {
  it('validates inputs and preserves the draft after a failed save without retrying', async () => {
    const user = userEvent.setup();
    fetchMock.mockImplementation(async path => String(path) === '/api/csrf' ? ok({token:'test',headerName:'X-CSRF-TOKEN'}) : ok({status:403},403));
    view(<BookingForm/>);
    await user.click(screen.getByRole('button',{name:'Buchung speichern'}));
    expect(screen.getByText('Bitte gib eine Beschreibung ein.')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
    await user.type(screen.getByLabelText('Beschreibung'),'Kaffee');
    await user.type(screen.getByLabelText('Betrag in EUR'),'12,345');
    await user.click(screen.getByRole('button',{name:'Buchung speichern'}));
    expect(await screen.findByRole('alert')).toHaveTextContent('Sicherheitsprüfung');
    expect(screen.getByLabelText('Beschreibung')).toHaveValue('Kaffee');
    expect(screen.getByLabelText('Betrag in EUR')).toHaveValue('12,345');
    expect(fetchMock).toHaveBeenCalledTimes(2);
    const init = fetchMock.mock.calls[1][1]!;
    expect(new Headers(init.headers).get('X-CSRF-TOKEN')).toBe('test');
    expect(JSON.parse(String(init.body)).amount).toBe('12.345');
  });
  it('shows server field errors while retaining form values', async () => {
    fetchMock.mockImplementation(async path => String(path) === '/api/csrf' ? ok({token:'test',headerName:'X-CSRF-TOKEN'}) : ok({status:400,errors:[{field:'description',message:'invalid'}]},400));
    view(<BookingForm booking={{id:1,type:'EXPENSE',description:'Test',amount:'1',date:'2026-11-03'}}/>);
    await userEvent.click(screen.getByRole('button',{name:'Buchung speichern'}));
    expect(await screen.findByText('Bitte prüfe dieses Feld.')).toBeInTheDocument();
    expect(screen.getByLabelText('Beschreibung')).toHaveAttribute('aria-invalid','true');
  });
});
describe('overview and sessions', () => {
  it('loads a changed period and shows an empty state', async () => {
    fetchMock.mockResolvedValue(ok([]));
    view(<Dashboard/>);
    expect(await screen.findByText('Noch ganz übersichtlich.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button',{name:'Vorheriger Monat'}));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    expect(fetchMock.mock.calls[0][0]).not.toEqual(fetchMock.mock.calls[1][0]);
  });
  it('clears private cached data and offers login after a 401', async () => {
    fetchMock.mockResolvedValueOnce(ok({id:'a',username:'Anna'}));
    client.setQueryData(['bookings','private'],[{description:'Privat'}]);
    view(<Routes><Route element={<Session/>}><Route index element={<p>Übersicht</p>}/></Route></Routes>);
    expect(await screen.findByText('Anna')).toBeInTheDocument();
    await act(async () => { window.dispatchEvent(new Event('session-expired')); });
    expect(await screen.findByRole('link',{name:'Mit Keycloak anmelden'})).toHaveAttribute('href','/login');
    expect(client.getQueryData(['bookings','private'])).toBeUndefined();
  });
  it('handles an anonymous initial API response without a redirect loop', async () => {
    fetchMock.mockResolvedValue(ok({status:401},401));
    view(<Session/>);
    expect(await screen.findByRole('link',{name:'Mit Keycloak anmelden'})).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
