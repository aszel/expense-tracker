import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter, Routes, Route, Link } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Session } from './auth/Session';
import { Dashboard } from './overview/Dashboard';
import { BookingPage } from './bookings/BookingForm';
import './styles.css';

const client = new QueryClient({
  defaultOptions: {
    queries: { retry: false, staleTime: 30_000 },
    mutations: { retry: false },
  },
});

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={client}>
      <BrowserRouter>
        <Routes>
          <Route element={<Session />}>
            <Route index element={<Dashboard />} />
            <Route path="bookings/new" element={<BookingPage />} />
            <Route path="bookings/:id/edit" element={<BookingPage />} />
            <Route path="*" element={
              <main className="page">
                <h1>Seite nicht gefunden.</h1>
                <Link to="/">Zur Übersicht</Link>
              </main>
            } />
          </Route>
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  </React.StrictMode>,
);
