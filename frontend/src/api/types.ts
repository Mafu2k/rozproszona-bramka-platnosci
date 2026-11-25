// Typy odpowiadające schematom z api/openapi.yaml (wersja 1.0.0).

export type KodSieci = 'VISA' | 'MC' | 'AMEX';
export const SIECI: KodSieci[] = ['VISA', 'MC', 'AMEX'];

export type Rola = 'ADMIN' | 'OPERATOR';
export type StatusKlienta = 'AKTYWNY' | 'ZABLOKOWANY' | 'USUNIETY';
export type StatusSklepu = 'AKTYWNY' | 'ZAWIESZONY' | 'USUNIETY';
export type StatusKarty = 'AKTYWNA' | 'ZABLOKOWANA';
export type StatusTransakcji = 'AUTORYZOWANA' | 'ODRZUCONA' | 'ZWROCONA';
export type TypTransakcji = 'AUTORYZACJA' | 'ZWROT';

export interface Blad {
  kod: string;
  komunikat: string;
  szczegoly?: Record<string, unknown> | null;
}

export interface Uzytkownik {
  id: number;
  login: string;
  rola: Rola;
  siec?: KodSieci | null;
}

export interface TokenLogowania {
  token: string;
  typ: 'Bearer';
  wygasa: string;
  uzytkownik: Uzytkownik;
}

/** Wspólne pola list: stronicowanie i informacja o awarii węzłów. */
export interface StronaBazowa {
  strona: number;
  rozmiar: number;
  lacznie: number;
  zrodlo?: string | null;
  czesciowy: boolean;
  brakujaceSieci: KodSieci[];
}

export interface Strona<T> extends StronaBazowa {
  elementy: T[];
}

export interface KlientDane {
  imie: string;
  nazwisko: string;
  email: string;
  telefon?: string | null;
  kraj: string;
}

export interface KlientZmiana extends KlientDane {
  status: 'AKTYWNY' | 'ZABLOKOWANY';
}

export interface Klient extends KlientDane {
  id: number;
  dataRejestracji: string;
  status: StatusKlienta;
}

export interface WynikBlokadyGlobalnej {
  idKlienta: number;
  zablokowaneKarty: Record<string, number>;
}

export interface SklepDane {
  nazwa: string;
  nip: string;
  kategoriaMcc: string;
  url?: string | null;
  emailKontaktowy: string;
}

export interface SklepZmiana extends SklepDane {
  status: 'AKTYWNY' | 'ZAWIESZONY';
}

export interface Sklep extends SklepDane {
  id: number;
  status: StatusSklepu;
  maKluczApi: boolean;
}

export interface NowyKluczApi {
  idSklepu: number;
  kluczApi?: string | null;
}

export interface Karta {
  id: number;
  idKlienta: number;
  siec: KodSieci;
  numerKarty: string;
  dataWaznosci: string;
  limitKredytowy: number;
  dostepneSrodki: number;
  waluta: string;
  status: StatusKarty;
  dataWydania: string;
}

export interface KartaNowa {
  idKlienta: number;
  limitKredytowy: number;
  waluta?: string;
  miesiaceWaznosci?: number;
}

export interface KartaWydana {
  numerKarty?: string | null;
  karta: Karta;
}

export interface ListaKart {
  elementy: Karta[];
  czesciowy: boolean;
  brakujaceSieci: KodSieci[];
}

export interface Transakcja {
  id: number;
  siec: KodSieci;
  idKarty: number;
  numerKarty?: string | null;
  idSklepu: number;
  idZadania: string;
  rrn: string;
  typ: TypTransakcji;
  kwota: number;
  waluta: string;
  dataTransakcji: string;
  status: StatusTransakcji;
  kod: string;
  komunikat: string;
  idTransakcjiPierwotnej?: number | null;
}

export interface TransakcjaSzczegoly {
  transakcja: Transakcja;
  pierwotna?: Transakcja;
  zwroty: Transakcja[];
}

export interface ObrotySieci {
  siec?: KodSieci | null;
  liczbaAutoryzacji: number;
  sumaAutoryzacji: number;
  liczbaZwrotow: number;
  sumaZwrotow: number;
}

export interface RaportObrotow {
  idSklepu: number;
  nazwaSklepu: string;
  od: string;
  do: string;
  sieci: ObrotySieci[];
  lacznie: ObrotySieci;
  czesciowy: boolean;
  brakujaceSieci: KodSieci[];
}

export interface StatystykaSieci {
  siec: KodSieci;
  liczbaTransakcji: number;
  sumaKwot: number;
  liczbaOdrzuconych: number;
  odsetekOdrzuconych: number;
}

export interface StatystykiSieci {
  od: string;
  do: string;
  sieci: StatystykaSieci[];
  czesciowy: boolean;
  brakujaceSieci: KodSieci[];
}

export interface StanReplikacji {
  ostatnieOdswiezenie?: string | null;
  opoznienieSekund?: number | null;
  przekroczonoLimit: boolean;
  ostatniBlad?: { czas?: string; komunikat?: string } | null;
}

export interface LogMv {
  tabela: string;
  liczbaWierszy: number;
  rozmiarBajtow: number;
}

export interface StanWezla {
  siec: KodSieci;
  nrWezla: number;
  rola: 'GLOWNY' | 'REPLIKA';
  dostepny: boolean;
  czasOdpowiedziMs?: number | null;
  replikacja?: StanReplikacji;
  logiMv?: LogMv[] | null;
  wiszaceTransakcje2pc?: number | null;
  blad?: string | null;
}

export interface StanWezlow {
  czasPomiaru: string;
  wezly: StanWezla[];
}

export interface ZadanieAutoryzacji {
  idZadania: string;
  numerKarty: string;
  kwota: number;
  waluta: string;
}

export interface ZadanieZwrotu {
  idZadania: string;
  rrnPierwotnej: string;
  kwota: number;
  waluta: string;
}

export interface WynikPlatnosci {
  idZadania: string;
  decyzja: 'ZATWIERDZONA' | 'ODRZUCONA';
  kod: string;
  komunikat: string;
  rrn?: string | null;
  typ: TypTransakcji;
  kwota: number;
  waluta: string;
  siec?: KodSieci | null;
  numerKarty?: string | null;
  statusTransakcji?: StatusTransakcji | null;
  dataTransakcji?: string | null;
  rrnPierwotnej?: string | null;
  powtorzona: boolean;
}
