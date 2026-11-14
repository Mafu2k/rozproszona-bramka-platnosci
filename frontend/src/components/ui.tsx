import { useEffect, useState, type ReactNode } from 'react';
import { Info, Warning, WarningOctagon, X } from '@phosphor-icons/react';
import type { KodSieci } from '../api/types';
import { ApiError } from '../api/client';
import { bledyPol, opisBledu } from '../lib/bledy';
import { nazwaSieci } from '../lib/format';

/* ---------- Banery danych częściowych i repliki (awaria węzła) ---------- */

interface InfoOZrodle {
  czesciowy?: boolean;
  brakujaceSieci?: KodSieci[];
  zrodlo?: string | null;
}

/** Ostrzeżenia z pól `czesciowy`, `brakujaceSieci` i `zrodlo`. Dane z pozostałych sieci są poprawne. */
export function BanerDanych({ dane }: { dane?: InfoOZrodle | null }) {
  if (!dane) return null;
  const brak = dane.czesciowy && dane.brakujaceSieci?.length ? dane.brakujaceSieci : null;
  const replika = dane.zrodlo && dane.zrodlo !== 'VISA' ? dane.zrodlo : null;
  if (!brak && !replika) return null;
  return (
    <div className="banery">
      {brak && (
        <div className="baner baner-ostrzezenie" role="status">
          <Warning aria-hidden weight="fill" className="baner-ikona" />
          <div>
          <strong>Dane częściowe.</strong> Brak danych z sieci {brak.map(nazwaSieci).join(', ')} – węzeł jest
          niedostępny. Dane pozostałych sieci są aktualne.
          </div>
        </div>
      )}
      {replika && (
        <div className="baner baner-info" role="status">
          <Info aria-hidden weight="fill" className="baner-ikona" />
          <div>
          <strong>Odczyt z repliki ({nazwaSieci(replika)}).</strong> Węzeł główny Visa jest niedostępny, dane mogą być
          do 60 s starsze. Dodawanie, edycja i usuwanie są chwilowo niemożliwe.
          </div>
        </div>
      )}
    </div>
  );
}

/** Czy zapisy danych wspólnych (klienci, sklepy) są możliwe: tylko gdy dane pochodzą z węzła głównego. */
export function zapisWspolnychMozliwy(zrodlo?: string | null): boolean {
  return !zrodlo || zrodlo === 'VISA';
}

/* ---------- Błędy ---------- */

export function BladApi({ blad, onZamknij }: { blad: unknown; onZamknij?: () => void }) {
  if (!blad) return null;
  const b = blad instanceof ApiError ? blad : null;
  const pola = bledyPol(blad);
  return (
    <div className="baner baner-blad" role="alert">
      <WarningOctagon aria-hidden weight="fill" className="baner-ikona" />
      <div className="baner-tresc">
        <strong>{opisBledu(blad)}</strong>
        {b && b.kod !== 'WALIDACJA' && b.message && opisBledu(blad) !== b.message && (
          <div className="maly">Szczegóły z serwera: {b.message}</div>
        )}
        {Object.keys(pola).length > 0 && (
          <ul className="lista-bledow">
            {Object.entries(pola).map(([k, v]) => (
              <li key={k}>
                <code>{k}</code>: {v}
              </li>
            ))}
          </ul>
        )}
        {b && <div className="maly wyciszony">HTTP {b.status || '—'} · kod {b.kod}</div>}
      </div>
      {onZamknij && (
        <button type="button" className="przycisk-ikona" onClick={onZamknij} aria-label="Zamknij komunikat">
          <X aria-hidden />
        </button>
      )}
    </div>
  );
}

/* ---------- Formularze ---------- */

export function Pole({
  etykieta,
  blad,
  podpowiedz,
  children,
}: {
  etykieta: string;
  blad?: string;
  podpowiedz?: string;
  children: ReactNode;
}) {
  return (
    <label className={`pole${blad ? ' pole-blad' : ''}`}>
      <span className="pole-etykieta">{etykieta}</span>
      {children}
      {blad ? <span className="pole-komunikat">{blad}</span> : podpowiedz && <span className="pole-podpowiedz">{podpowiedz}</span>}
    </label>
  );
}

/* ---------- Modal ---------- */

export function Modal({
  tytul,
  onZamknij,
  children,
  stopka,
  szeroki,
  tylkoPrzyciskiem,
}: {
  tytul: string;
  onZamknij: () => void;
  children: ReactNode;
  stopka?: ReactNode;
  szeroki?: boolean;
  tylkoPrzyciskiem?: boolean;
}) {
  useEffect(() => {
    if (tylkoPrzyciskiem) return;
    const esc = (e: KeyboardEvent) => e.key === 'Escape' && onZamknij();
    window.addEventListener('keydown', esc);
    return () => window.removeEventListener('keydown', esc);
  }, [onZamknij, tylkoPrzyciskiem]);

  return (
    <div className="modal-tlo" onMouseDown={(e) => !tylkoPrzyciskiem && e.target === e.currentTarget && onZamknij()}>
      <div className={`modal${szeroki ? ' modal-szeroki' : ''}`} role="dialog" aria-modal="true" aria-label={tytul}>
        <header className="modal-naglowek">
          <h2>{tytul}</h2>
          {!tylkoPrzyciskiem && (
            <button type="button" className="przycisk-ikona" onClick={onZamknij} aria-label="Zamknij okno">
              <X aria-hidden />
            </button>
          )}
        </header>
        <div className="modal-tresc">{children}</div>
        {stopka && <footer className="modal-stopka">{stopka}</footer>}
      </div>
    </div>
  );
}

/** Potwierdzenie operacji, zwłaszcza nieodwracalnych (usunięcie, blokada). */
export function Potwierdzenie({
  tytul,
  tresc,
  przycisk,
  niebezpieczne,
  trwa,
  blad,
  onTak,
  onNie,
}: {
  tytul: string;
  tresc: ReactNode;
  przycisk: string;
  niebezpieczne?: boolean;
  trwa?: boolean;
  blad?: unknown;
  onTak: () => void;
  onNie: () => void;
}) {
  return (
    <Modal
      tytul={tytul}
      onZamknij={onNie}
      tylkoPrzyciskiem={trwa}
      stopka={
        <>
          <button type="button" className="przycisk" onClick={onNie} disabled={trwa}>
            Anuluj
          </button>
          <button
            type="button"
            className={`przycisk ${niebezpieczne ? 'przycisk-niebezpieczny' : 'przycisk-glowny'}`}
            onClick={onTak}
            disabled={trwa}
          >
            {trwa ? 'Trwa…' : przycisk}
          </button>
        </>
      }
    >
      {tresc}
      <BladApi blad={blad} />
    </Modal>
  );
}

/** Wartość pokazywana tylko raz (pełny numer karty, klucz API sklepu). */
export function JednorazowaWartosc({ etykieta, wartosc }: { etykieta: string; wartosc: string }) {
  const [skopiowano, setSkopiowano] = useState(false);
  return (
    <div className="jednorazowa">
      <div className="maly">{etykieta}</div>
      <div className="jednorazowa-wartosc">
        <code>{wartosc}</code>
        <button
          type="button"
          className="przycisk przycisk-maly"
          onClick={async () => {
            try {
              await navigator.clipboard.writeText(wartosc);
              setSkopiowano(true);
            } catch {
              setSkopiowano(false);
            }
          }}
        >
          {skopiowano ? 'Skopiowano' : 'Kopiuj'}
        </button>
      </div>
      <div className="baner baner-ostrzezenie maly">
        Ta wartość jest pokazywana <strong>tylko raz</strong>. Baza przechowuje wyłącznie jej skrót lub maskę.
      </div>
    </div>
  );
}

/* ---------- Tabele i listy ---------- */

export function Paginacja({
  strona,
  rozmiar,
  lacznie,
  onStrona,
}: {
  strona: number;
  rozmiar: number;
  lacznie: number;
  onStrona: (s: number) => void;
}) {
  const stron = Math.max(1, Math.ceil(lacznie / rozmiar));
  const od = lacznie === 0 ? 0 : strona * rozmiar + 1;
  const doo = Math.min(lacznie, (strona + 1) * rozmiar);
  return (
    <div className="paginacja">
      <span className="wyciszony">
        {od}–{doo} z {lacznie}
      </span>
      <div className="paginacja-przyciski">
        <button type="button" className="przycisk przycisk-maly" disabled={strona === 0} onClick={() => onStrona(strona - 1)}>
          ‹ Poprzednia
        </button>
        <span>
          {strona + 1} / {stron}
        </span>
        <button
          type="button"
          className="przycisk przycisk-maly"
          disabled={strona + 1 >= stron}
          onClick={() => onStrona(strona + 1)}
        >
          Następna ›
        </button>
      </div>
    </div>
  );
}

const KOLORY_STATUSU: Record<string, string> = {
  AKTYWNY: 'ok',
  AKTYWNA: 'ok',
  AUTORYZOWANA: 'ok',
  ZATWIERDZONA: 'ok',
  ZABLOKOWANY: 'blad',
  ZABLOKOWANA: 'blad',
  ODRZUCONA: 'blad',
  ZAWIESZONY: 'ostrzezenie',
  ZWROCONA: 'info',
  ZWROT: 'info',
  USUNIETY: 'neutralny',
};

export function Status({ wartosc }: { wartosc: string | null | undefined }) {
  if (!wartosc) return <span className="wyciszony">—</span>;
  return <span className={`znaczek znaczek-${KOLORY_STATUSU[wartosc] ?? 'neutralny'}`}>{wartosc}</span>;
}

export function Siec({ kod }: { kod: string | null | undefined }) {
  if (!kod) return <span className="wyciszony">—</span>;
  return <span className={`siec siec-${kod.toLowerCase()}`}>{nazwaSieci(kod)}</span>;
}

export function CzasOdpowiedzi({ ms, laduje }: { ms?: number; laduje?: boolean }) {
  if (laduje) return <span className="czas wyciszony">pobieranie…</span>;
  if (ms === undefined) return null;
  return (
    <span className="czas wyciszony" title="Czas od wysłania żądania do otrzymania odpowiedzi">
      czas odpowiedzi: {ms} ms
    </span>
  );
}

export function Ladowanie() {
  return <div className="ladowanie">Ładowanie…</div>;
}

export function Pusto({ children }: { children: ReactNode }) {
  return <div className="pusto">{children}</div>;
}

export function NaglowekStrony({
  tytul,
  opis,
  akcje,
}: {
  tytul: string;
  opis?: ReactNode;
  akcje?: ReactNode;
}) {
  return (
    <div className="naglowek-strony">
      <div>
        <h1>{tytul}</h1>
        {opis && <p className="wyciszony">{opis}</p>}
      </div>
      {akcje && <div className="akcje">{akcje}</div>}
    </div>
  );
}

export function Szczegoly({ wiersze }: { wiersze: [string, ReactNode][] }) {
  return (
    <dl className="szczegoly">
      {wiersze.map(([k, v]) => (
        <div key={k}>
          <dt>{k}</dt>
          <dd>{v ?? '—'}</dd>
        </div>
      ))}
    </dl>
  );
}
