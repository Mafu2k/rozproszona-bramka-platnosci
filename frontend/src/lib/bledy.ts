import { ApiError } from '../api/client';
import { nazwaSieci } from './format';

export function jakoApiError(e: unknown): ApiError {
  if (e instanceof ApiError) return e;
  return new ApiError(0, { kod: 'NIEZNANY', komunikat: e instanceof Error ? e.message : String(e) }, null);
}

/** Zrozumiały dla użytkownika opis błędu z kontraktu (docs/notatka-dla-frontendu.md, sekcja „Błędy”). */
export function opisBledu(e: unknown): string {
  const b = jakoApiError(e);
  const poSekundach = b.retryAfter ? ` Spróbuj ponownie za ${b.retryAfter} s.` : '';

  switch (b.kod) {
    case 'WALIDACJA':
      return 'Formularz zawiera błędy. Popraw zaznaczone pola.';
    case 'BRAK_KLUCZA_IDEMPOTENCJI':
      return 'Błąd aplikacji: brak nagłówka Idempotency-Key.';
    case 'BRAK_UWIERZYTELNIENIA':
      return 'Sesja wygasła. Zaloguj się ponownie.';
    case 'NIEPRAWIDLOWE_DANE_LOGOWANIA':
      return 'Nieprawidłowy login lub hasło.';
    case 'BRAK_UPRAWNIEN':
      return 'Brak uprawnień do tej operacji albo do danych tej sieci.';
    case 'NIE_ZNALEZIONO':
      return 'Nie znaleziono rekordu.';
    case '94':
      return 'To żądanie zostało już wysłane z innymi danymi. Wyślij formularz jeszcze raz.';
    case 'REPLIKACJA_W_TOKU':
      return `Dane jeszcze się synchronizują między węzłami.${poSekundach || ' Spróbuj ponownie za chwilę (zwykle do 30 s).'}`;
    case 'ZBYT_WIELE_PROB':
      return `Zbyt wiele nieudanych prób logowania.${poSekundach}`;
    case '91': {
      const siec = b.szczegoly?.siec;
      return `Sieć ${siec ? nazwaSieci(String(siec)) : ''} jest niedostępna. Nic nie zostało zmienione.`.replace('  ', ' ');
    }
    case '96':
      return 'Błąd systemu. Spróbuj ponownie później.';
    case 'BRAK_POLACZENIA':
      return 'Brak połączenia z serwerem API. Sprawdź, czy backend albo mock jest uruchomiony.';
    default:
      return b.message || `Błąd ${b.status}`;
  }
}

/** Błędy pól z odpowiedzi WALIDACJA: { nazwaPola: komunikat }. */
export function bledyPol(e: unknown): Record<string, string> {
  const b = e instanceof ApiError ? e : null;
  if (!b || b.kod !== 'WALIDACJA' || !b.szczegoly) return {};
  return Object.fromEntries(Object.entries(b.szczegoly).map(([k, v]) => [k, String(v)]));
}
