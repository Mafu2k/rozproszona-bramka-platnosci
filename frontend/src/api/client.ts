import type { Blad } from './types';

const BAZA = '/api/v1';

/** Błąd API w formacie kontraktu: { kod, komunikat, szczegoly }. */
export class ApiError extends Error {
  readonly status: number;
  readonly kod: string;
  readonly szczegoly: Record<string, unknown> | null;
  readonly retryAfter: number | null;

  constructor(status: number, blad: Blad, retryAfter: number | null) {
    super(blad.komunikat);
    this.status = status;
    this.kod = blad.kod;
    this.szczegoly = blad.szczegoly ?? null;
    this.retryAfter = retryAfter;
  }

  /** Błąd po stronie sieci/serwera, przy którym warto ponowić to samo żądanie z tym samym kluczem. */
  get moznaPonowic(): boolean {
    return this.status === 0 || this.status >= 500;
  }
}

/** Wynik zapytania razem z czasem odpowiedzi (przydaje się do zrzutów do testów). */
export interface Zmierzone<T> {
  dane: T;
  ms: number;
}

let token: string | null = null;
let poBrakuUwierzytelnienia: (() => void) | null = null;

export function ustawToken(nowy: string | null) {
  token = nowy;
}

/** Wywoływane przy 401 na dowolnym żądaniu panelu: powrót do ekranu logowania. */
export function przyBrakuUwierzytelnienia(fn: (() => void) | null) {
  poBrakuUwierzytelnienia = fn;
}

export function nowyKluczIdempotencji(): string {
  return crypto.randomUUID();
}

type Parametry = Record<string, string | number | boolean | null | undefined>;

interface Opcje {
  metoda?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  parametry?: Parametry;
  cialo?: unknown;
  kluczIdempotencji?: string;
  kluczApi?: string;
  bezTokenu?: boolean;
}

function zbudujAdres(sciezka: string, parametry?: Parametry): string {
  const qs = new URLSearchParams();
  for (const [k, v] of Object.entries(parametry ?? {})) {
    if (v !== undefined && v !== null && v !== '') qs.set(k, String(v));
  }
  const s = qs.toString();
  return `${BAZA}${sciezka}${s ? `?${s}` : ''}`;
}

async function wykonaj<T>(sciezka: string, o: Opcje = {}): Promise<Zmierzone<T>> {
  const naglowki: Record<string, string> = { Accept: 'application/json' };
  if (o.cialo !== undefined) naglowki['Content-Type'] = 'application/json';
  if (token && !o.bezTokenu && !o.kluczApi) naglowki.Authorization = `Bearer ${token}`;
  if (o.kluczIdempotencji) naglowki['Idempotency-Key'] = o.kluczIdempotencji;
  if (o.kluczApi) naglowki['X-Api-Key'] = o.kluczApi;

  const start = performance.now();
  let odp: Response;
  try {
    odp = await fetch(zbudujAdres(sciezka, o.parametry), {
      method: o.metoda ?? 'GET',
      headers: naglowki,
      body: o.cialo !== undefined ? JSON.stringify(o.cialo) : undefined,
    });
  } catch {
    throw new ApiError(0, { kod: 'BRAK_POLACZENIA', komunikat: 'Brak połączenia z serwerem API' }, null);
  }
  const ms = Math.round(performance.now() - start);

  const tekst = await odp.text();
  let json: unknown = null;
  if (tekst) {
    try {
      json = JSON.parse(tekst);
    } catch {
      json = null;
    }
  }

  if (!odp.ok) {
    const blad: Blad =
      json && typeof json === 'object' && 'kod' in json
        ? (json as Blad)
        : { kod: String(odp.status), komunikat: `Błąd HTTP ${odp.status}` };
    const ra = odp.headers.get('Retry-After');
    const err = new ApiError(odp.status, blad, ra ? Number(ra) : null);
    if (odp.status === 401 && !o.bezTokenu && !o.kluczApi) poBrakuUwierzytelnienia?.();
    throw err;
  }

  return { dane: json as T, ms };
}

export const api = {
  get: <T>(sciezka: string, parametry?: Parametry) => wykonaj<T>(sciezka, { parametry }),

  /** Zapis z panelu. Klucz idempotencji jest wymagany przez backend przy każdym POST/PUT/DELETE. */
  zapis: <T>(
    metoda: 'POST' | 'PUT' | 'DELETE',
    sciezka: string,
    kluczIdempotencji: string,
    cialo?: unknown,
  ) => wykonaj<T>(sciezka, { metoda, cialo, kluczIdempotencji }),

  logowanie: <T>(cialo: unknown) => wykonaj<T>('/auth/logowanie', { metoda: 'POST', cialo, bezTokenu: true }),
  wylogowanie: () => wykonaj<void>('/auth/wylogowanie', { metoda: 'POST' }),

  /** Żądania sklepu (płatności) uwierzytelniane kluczem API zamiast tokenem panelu. */
  sklep: <T>(metoda: 'GET' | 'POST', sciezka: string, kluczApi: string, cialo?: unknown) =>
    wykonaj<T>(sciezka, { metoda, cialo, kluczApi }),
};
