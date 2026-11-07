export interface Problem {
  status: number;
  detail?: string;
  errors?: { field: string; message: string }[];
}

export class ApiError extends Error {
  constructor(public problem: Problem) {
    super(problem.detail ?? 'Die Anfrage ist fehlgeschlagen.');
  }
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set('Accept', 'application/json');
  let response: Response;
  try {
    response = await fetch(path, { ...init, credentials: 'same-origin', headers });
  } catch {
    throw new Error('Der Server ist nicht erreichbar. Bitte versuche es erneut.');
  }
  if (!response.ok) {
    const body: unknown = await response.json().catch(() => null);
    const problem: Problem = { ...(body && typeof body === 'object' ? body : {}), status: response.status };
    if (response.status === 401) window.dispatchEvent(new Event('session-expired'));
    throw new ApiError(problem);
  }
  if (response.status === 204) return undefined as T;
  try {
    return await response.json();
  } catch {
    throw new Error('Die Serverantwort konnte nicht gelesen werden. Bitte versuche es erneut.');
  }
}

export async function csrf() {
  return api<{ token: string; headerName: string }>('/api/csrf');
}

export async function write<T>(path: string, method: string, body?: unknown) {
  const token = await csrf();
  return api<T>(path, {
    method,
    headers: { 'Content-Type': 'application/json', [token.headerName]: token.token },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

export function errorMessage(error: unknown) {
  if (error instanceof ApiError) {
    if (error.problem.status === 403) {
      return 'Die Sicherheitsprüfung ist fehlgeschlagen. Bitte melde dich erneut an und versuche es noch einmal.';
    }
    if (error.problem.status === 404) return 'Diese Buchung ist nicht mehr verfügbar.';
    if (error.problem.status === 400) return 'Bitte prüfe deine Angaben.';
    return 'Die Anfrage ist fehlgeschlagen. Bitte versuche es erneut.';
  }
  return error instanceof Error ? error.message : 'Ein unerwarteter Fehler ist aufgetreten.';
}
