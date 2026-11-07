import { expect, test } from '@playwright/test';

test('create, edit, delete and show a responsive overview with a mocked session', async ({page}) => {
  let bookings: {id:number;type:string;description:string;amount:string;date:string}[] = [];
  await page.route('**/api/**', async route => {
    const req = route.request(), path = new URL(req.url()).pathname;
    if (path === '/api/me') return route.fulfill({json:{id:'a',username:'Anna'}});
    if (path === '/api/csrf') return route.fulfill({json:{token:'test',headerName:'X-CSRF-TOKEN'}});
    if (path.startsWith('/api/bookings')) {
      if (req.method() === 'POST') { const b = {id:1,...req.postDataJSON()}; bookings.push(b); return route.fulfill({status:201,json:b}); }
      if (req.method() === 'PUT') { bookings = [{id:1,...req.postDataJSON()}]; return route.fulfill({json:bookings[0]}); }
      if (req.method() === 'DELETE') { bookings=[]; return route.fulfill({status:204}); }
      return route.fulfill({json:path === '/api/bookings' ? bookings : bookings[0]});
    }
    return route.fulfill({status:404});
  });
  await page.goto('/');
  await expect(page.getByText('Noch ganz übersichtlich.')).toBeVisible();
  await page.getByRole('link',{name:'Neue Buchung'}).click();
  await page.getByLabel('Einnahme',{exact:false}).check();
  await page.getByLabel('Beschreibung').fill('Honorar');
  await page.getByLabel('Betrag in EUR').fill('123,456789');
  await page.getByRole('button',{name:'Buchung speichern'}).click();
  await expect(page.getByTestId('income')).toHaveText('123,456789 €');
  await page.getByRole('link',{name:'Honorar bearbeiten'}).click();
  await page.getByLabel('Beschreibung').fill('Honorar korrigiert');
  await page.getByLabel('Betrag in EUR').fill('125,50');
  await page.getByRole('button',{name:'Buchung speichern'}).click();
  await expect(page.getByTestId('income')).toHaveText('125,50 €');
  await expect(page.getByText('Honorar korrigiert',{exact:true})).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
  await page.getByRole('button',{name:'Honorar korrigiert löschen'}).click();
  await expect(page.getByRole('button', {name:'Abbrechen'})).toBeFocused();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).not.toBeVisible();
  await page.getByRole('button',{name:'Honorar korrigiert löschen'}).click();
  await page.getByRole('button',{name:'Endgültig löschen'}).click();
  await expect(page.getByText('Noch ganz übersichtlich.')).toBeVisible();
});
