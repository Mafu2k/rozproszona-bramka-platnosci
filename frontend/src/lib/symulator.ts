/** Klucz API sklepu zapamiętany na czas sesji przeglądarki, żeby nie wklejać go ręcznie w symulatorze. */
export interface KluczSymulatora {
  kluczApi: string;
  idSklepu?: number;
  nazwaSklepu?: string;
}

const KLUCZ = 'bramka.symulator';

export function zapiszKluczSymulatora(k: KluczSymulatora | null) {
  try {
    if (k) sessionStorage.setItem(KLUCZ, JSON.stringify(k));
    else sessionStorage.removeItem(KLUCZ);
  } catch {
    /* brak dostępu do sessionStorage */
  }
}

export function wczytajKluczSymulatora(): KluczSymulatora | null {
  try {
    const s = sessionStorage.getItem(KLUCZ);
    return s ? (JSON.parse(s) as KluczSymulatora) : null;
  } catch {
    return null;
  }
}
