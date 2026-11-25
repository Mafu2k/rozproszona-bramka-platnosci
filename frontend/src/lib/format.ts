import type { KodSieci } from '../api/types';

export const NAZWY_SIECI: Record<KodSieci, string> = {
  VISA: 'Visa',
  MC: 'MasterCard',
  AMEX: 'American Express',
};

export function nazwaSieci(kod: string | null | undefined): string {
  if (!kod) return '—';
  return NAZWY_SIECI[kod as KodSieci] ?? kod;
}

const formatKwoty = new Intl.NumberFormat('pl-PL', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/** Kwota z groszy, np. 12999 -> "129,99 PLN". */
export function kwota(grosze: number | null | undefined, waluta = 'PLN'): string {
  if (grosze === null || grosze === undefined) return '—';
  return `${formatKwoty.format(grosze / 100)} ${waluta}`;
}

/** Tekst z pola formularza ("129,99" albo "129.99") na grosze. Zwraca null, gdy wartość jest niepoprawna. */
export function naGrosze(tekst: string): number | null {
  const t = tekst.trim().replace(/\s/g, '').replace(',', '.');
  if (!/^\d+(\.\d{1,2})?$/.test(t)) return null;
  return Math.round(Number(t) * 100);
}

const formatProcentu = new Intl.NumberFormat('pl-PL', { minimumFractionDigits: 1, maximumFractionDigits: 1 });

/** Wartość w procentach, np. 44.7 -> "44,7%". */
export function procent(wartosc: number): string {
  return `${formatProcentu.format(wartosc)}%`;
}

export function groszeNaTekst(grosze: number): string {
  return (grosze / 100).toFixed(2).replace('.', ',');
}

const formatDaty = new Intl.DateTimeFormat('pl-PL', { dateStyle: 'short', timeStyle: 'medium' });
const formatDnia = new Intl.DateTimeFormat('pl-PL', { dateStyle: 'short' });

/** Data z UTC (ISO 8601) w czasie lokalnym. */
export function data(iso: string | null | undefined): string {
  if (!iso) return '—';
  return formatDaty.format(new Date(iso));
}

export function dzien(iso: string | null | undefined): string {
  if (!iso) return '—';
  return formatDnia.format(new Date(iso));
}

/** Wartość <input type="date"> (dzień lokalny) na początek tego dnia w UTC. */
export function poczatekDnia(dzienLokalny: string): string {
  return new Date(`${dzienLokalny}T00:00:00`).toISOString();
}

/** Koniec okresu jest wyłączny, więc "do 2026-11-13" oznacza początek 14 listopada. */
export function koniecDnia(dzienLokalny: string): string {
  const d = new Date(`${dzienLokalny}T00:00:00`);
  d.setDate(d.getDate() + 1);
  return d.toISOString();
}

export function dzisiaj(przesuniecieDni = 0): string {
  const d = new Date();
  d.setDate(d.getDate() + przesuniecieDni);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

/** Numer karty w grupach po 4 cyfry. */
export function grupujNumer(numer: string): string {
  return numer.replace(/(.{4})/g, '$1 ').trim();
}
