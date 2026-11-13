import { useCallback, useRef, useState } from 'react';
import { ApiError, nowyKluczIdempotencji } from '../api/client';
import { jakoApiError } from './bledy';

/**
 * Obsługa jednej akcji zapisu (np. formularza) z kluczem idempotencji.
 *
 * - Klucz powstaje razem z formularzem i jest wysyłany przy każdej próbie.
 * - Po błędzie sieci albo serwera (5xx) klucz zostaje, więc ponowienie nie wykona operacji drugi raz.
 * - Po sukcesie albo błędzie 4xx powstaje nowy klucz, bo kolejne kliknięcie to już nowa akcja.
 */
export function useZapis<A, W>(akcja: (klucz: string, argumenty: A) => Promise<W>) {
  const klucz = useRef(nowyKluczIdempotencji());
  const [trwa, setTrwa] = useState(false);
  const [blad, setBlad] = useState<ApiError | null>(null);

  const wykonaj = useCallback(
    async (argumenty: A): Promise<W | undefined> => {
      setTrwa(true);
      setBlad(null);
      try {
        const wynik = await akcja(klucz.current, argumenty);
        klucz.current = nowyKluczIdempotencji();
        return wynik;
      } catch (e) {
        const err = jakoApiError(e);
        if (!err.moznaPonowic) klucz.current = nowyKluczIdempotencji();
        setBlad(err);
        return undefined;
      } finally {
        setTrwa(false);
      }
    },
    [akcja],
  );

  return { wykonaj, trwa, blad, wyczyscBlad: () => setBlad(null) };
}
