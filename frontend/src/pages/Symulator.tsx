import { useState, type FormEvent } from 'react';
import { ArrowCounterClockwise, Lightning, Storefront } from '@phosphor-icons/react';
import { api } from '../api/client';
import type { WynikPlatnosci } from '../api/types';
import { BladApi, NaglowekStrony, Pole, Siec, Status, Szczegoly } from '../components/ui';
import { jakoApiError, opisBledu } from '../lib/bledy';
import { data, groszeNaTekst, kwota, naGrosze } from '../lib/format';
import { wczytajKluczSymulatora, zapiszKluczSymulatora } from '../lib/symulator';

/** Publiczne numery testowe z README backendu (karty demo wydawane przy pierwszym starcie). */
const KARTY_TESTOWE = [
  { numer: '4111111111111111', siec: 'VISA' },
  { numer: '4012888888881881', siec: 'VISA' },
  { numer: '5555555555554444', siec: 'MC' },
  { numer: '5105105105105100', siec: 'MC' },
  { numer: '2223003122003222', siec: 'MC' },
  { numer: '378282246310005', siec: 'AMEX' },
  { numer: '371449635398431', siec: 'AMEX' },
];

interface DoZwrotu {
  rrn: string;
  kwota: number;
  waluta: string;
  numer: number;
}

const noweIdZadania = (prefiks: string) => `${prefiks}-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 6)}`;

export function Symulator() {
  const zapamietany = wczytajKluczSymulatora();
  const [kluczApi, setKluczApi] = useState(zapamietany?.kluczApi ?? '');
  const [historia, setHistoria] = useState<WynikPlatnosci[]>([]);
  const [rrnDoZwrotu, setRrnDoZwrotu] = useState<DoZwrotu | null>(null);

  function zapiszKlucz(v: string) {
    setKluczApi(v);
    zapiszKluczSymulatora(v ? { kluczApi: v } : null);
  }

  const dodaj = (w: WynikPlatnosci) => setHistoria((h) => [w, ...h].slice(0, 20));
  const zwroconoZ = (rrn: string) =>
    historia
      .filter((z) => z.typ === 'ZWROT' && z.decyzja === 'ZATWIERDZONA' && z.rrnPierwotnej === rrn)
      .reduce((suma, z) => suma + z.kwota, 0);

  return (
    <>
      <NaglowekStrony
        tytul="Symulator sklepu"
        opis="FR2/FR3 · sklep wysyła płatność do bramki, a bramka po numerze BIN kieruje ją do węzła właściwej sieci."
      />

      <section className="panel">
        <div className="panel-naglowek">
          <h2>
            <Storefront aria-hidden /> Sklep
          </h2>
        </div>
        <Pole
          etykieta="Klucz API sklepu (nagłówek X-Api-Key)"
          podpowiedz={
            zapamietany?.nazwaSklepu
              ? `Klucz sklepu „${zapamietany.nazwaSklepu}”.`
              : 'Klucz generuje administrator: Sklepy → Klucz API → „Użyj w symulatorze”.'
          }
        >
          <input value={kluczApi} onChange={(e) => zapiszKlucz(e.target.value.trim())} placeholder="bp_…" spellCheck={false} />
        </Pole>
      </section>

      <div className="siatka-symulatora">
        <Autoryzacja kluczApi={kluczApi} onWynik={dodaj} />
        <Zwrot kluczApi={kluczApi} onWynik={dodaj} wstepny={rrnDoZwrotu} key={rrnDoZwrotu?.numer ?? 'zwrot'} />
      </div>

      {historia.length > 0 && (
        <section className="panel">
          <h2>Wyniki w tej sesji</h2>
          <div className="tabela-kontener">
            <table className="tabela">
              <thead>
                <tr>
                  <th>Czas</th>
                  <th>Typ</th>
                  <th>Decyzja</th>
                  <th>Kod</th>
                  <th>RRN</th>
                  <th>Sieć</th>
                  <th>Karta</th>
                  <th className="liczba">Kwota</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {historia.map((w, i) => (
                  <tr key={`${w.idZadania}-${i}`}>
                    <td>{data(w.dataTransakcji)}</td>
                    <td>{w.typ}</td>
                    <td>
                      <Status wartosc={w.decyzja} />
                      {w.powtorzona && <span className="znaczek znaczek-neutralny">powtórzona</span>}
                    </td>
                    <td title={w.komunikat}>
                      <code>{w.kod}</code>
                    </td>
                    <td>
                      <code>{w.rrn ?? '—'}</code>
                    </td>
                    <td>
                      <Siec kod={w.siec} />
                    </td>
                    <td>
                      <code>{w.numerKarty ?? '—'}</code>
                    </td>
                    <td className="liczba">{kwota(w.kwota, w.waluta)}</td>
                    <td className="akcje-wiersza">
                      {w.typ === 'AUTORYZACJA' && w.decyzja === 'ZATWIERDZONA' && w.rrn && zwroconoZ(w.rrn) < w.kwota && (
                        <button
                          type="button"
                          className="przycisk przycisk-maly"
                          onClick={() =>
                            setRrnDoZwrotu({ rrn: w.rrn!, kwota: w.kwota - zwroconoZ(w.rrn!), waluta: w.waluta, numer: Date.now() })
                          }
                        >
                          <ArrowCounterClockwise aria-hidden /> Zwrot
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}

      <TestObciazenia kluczApi={kluczApi} />
    </>
  );
}

function WynikDecyzji({ w }: { w: WynikPlatnosci }) {
  const ok = w.decyzja === 'ZATWIERDZONA';
  return (
    <div className={`wynik-platnosci ${ok ? 'wynik-ok' : 'wynik-odrzucona'}`} role="status">
      <div className="wynik-naglowek">
        <Status wartosc={w.decyzja} />
        <span>
          kod <code>{w.kod}</code> – {w.komunikat}
        </span>
      </div>
      <Szczegoly
        wiersze={[
          ['RRN', <code key="r">{w.rrn ?? '—'}</code>],
          ['Sieć (węzeł)', <Siec key="s" kod={w.siec} />],
          ['Karta', <code key="k">{w.numerKarty ?? '—'}</code>],
          ['Kwota', kwota(w.kwota, w.waluta)],
          ['Status transakcji', w.statusTransakcji ?? '—'],
          ['Powtórzone żądanie', w.powtorzona ? 'tak – zwrócono zapisany wynik' : 'nie'],
        ]}
      />
    </div>
  );
}

function Autoryzacja({ kluczApi, onWynik }: { kluczApi: string; onWynik: (w: WynikPlatnosci) => void }) {
  const [numer, setNumer] = useState(KARTY_TESTOWE[0].numer);
  const [kwotaTekst, setKwota] = useState('129,99');
  const [waluta, setWaluta] = useState('PLN');
  const [idZadania, setIdZadania] = useState(() => noweIdZadania('zam'));
  const [wynik, setWynik] = useState<WynikPlatnosci | null>(null);
  const [blad, setBlad] = useState<unknown>(null);
  const [trwa, setTrwa] = useState(false);

  async function zaplac(e: FormEvent) {
    e.preventDefault();
    const grosze = naGrosze(kwotaTekst);
    if (grosze === null || grosze < 1) return setBlad(new Error('Podaj kwotę, np. 129,99'));
    setTrwa(true);
    setBlad(null);
    setWynik(null);
    try {
      const { dane } = await api.sklep<WynikPlatnosci>('POST', '/platnosci/autoryzacje', kluczApi, {
        idZadania,
        numerKarty: numer.replace(/\s/g, ''),
        kwota: grosze,
        waluta,
      });
      setWynik(dane);
      onWynik(dane);
      // Nowe zamówienie dostaje nowy identyfikator. Ponowienie po błędzie sieci używa tego samego.
      setIdZadania(noweIdZadania('zam'));
    } catch (err) {
      setBlad(err);
    } finally {
      setTrwa(false);
    }
  }

  return (
    <section className="panel">
      <h2>Autoryzacja płatności</h2>
      <form className="formularz" onSubmit={zaplac}>
        <Pole etykieta="Numer karty" podpowiedz="Sieć rozpoznawana po BIN: 4… Visa, 51–55/2221–2720 MasterCard, 34/37 AmEx">
          <input
            value={numer}
            onChange={(e) => setNumer(e.target.value)}
            inputMode="numeric"
            autoComplete="off"
            required
            pattern="[0-9 ]{12,23}"
          />
        </Pole>
        <div className="karty-testowe" role="group" aria-label="Karty testowe">
          {KARTY_TESTOWE.map((k) => (
            <button
              key={k.numer}
              type="button"
              className={`chip siec-${k.siec.toLowerCase()}${numer === k.numer ? ' chip-aktywny' : ''}`}
              onClick={() => setNumer(k.numer)}
              aria-pressed={numer === k.numer}
            >
              {k.numer.slice(0, 4)}…{k.numer.slice(-4)}
            </button>
          ))}
        </div>
        <div className="siatka-2">
          <Pole etykieta="Kwota">
            <input value={kwotaTekst} onChange={(e) => setKwota(e.target.value)} inputMode="decimal" required />
          </Pole>
          <Pole etykieta="Waluta">
            <input value={waluta} onChange={(e) => setWaluta(e.target.value.toUpperCase())} pattern="[A-Z]{3}" maxLength={3} />
          </Pole>
        </div>
        <Pole etykieta="ID żądania (idZadania)" podpowiedz="Ten sam identyfikator = to samo zamówienie. Bramka nie obciąży karty drugi raz.">
          <input value={idZadania} onChange={(e) => setIdZadania(e.target.value)} required spellCheck={false} />
        </Pole>
        <button type="submit" className="przycisk przycisk-glowny" disabled={trwa || !kluczApi}>
          {trwa ? 'Autoryzacja…' : 'Zapłać'}
        </button>
        {!kluczApi && <p className="maly wyciszony">Wpisz najpierw klucz API sklepu.</p>}
      </form>
      <BladApi blad={blad} />
      {wynik && <WynikDecyzji w={wynik} />}
    </section>
  );
}

function Zwrot({
  kluczApi,
  onWynik,
  wstepny,
}: {
  kluczApi: string;
  onWynik: (w: WynikPlatnosci) => void;
  wstepny: DoZwrotu | null;
}) {
  const [rrn, setRrn] = useState(wstepny?.rrn ?? '');
  const [kwotaTekst, setKwota] = useState(wstepny ? groszeNaTekst(wstepny.kwota) : '');
  const [waluta, setWaluta] = useState(wstepny?.waluta ?? 'PLN');
  const [idZadania, setIdZadania] = useState(() => noweIdZadania('zwrot'));
  const [wynik, setWynik] = useState<WynikPlatnosci | null>(null);
  const [blad, setBlad] = useState<unknown>(null);
  const [trwa, setTrwa] = useState(false);

  async function zwroc(e: FormEvent) {
    e.preventDefault();
    const grosze = naGrosze(kwotaTekst);
    if (grosze === null || grosze < 1) return setBlad(new Error('Podaj kwotę zwrotu'));
    setTrwa(true);
    setBlad(null);
    setWynik(null);
    try {
      const { dane } = await api.sklep<WynikPlatnosci>('POST', '/platnosci/zwroty', kluczApi, {
        idZadania,
        rrnPierwotnej: rrn,
        kwota: grosze,
        waluta,
      });
      setWynik(dane);
      onWynik(dane);
      setIdZadania(noweIdZadania('zwrot'));
    } catch (err) {
      setBlad(err);
    } finally {
      setTrwa(false);
    }
  }

  return (
    <section className="panel">
      <h2>Zwrot</h2>
      <form className="formularz" onSubmit={zwroc}>
        <Pole etykieta="RRN transakcji pierwotnej" podpowiedz="12 cyfr. Kliknij „Zwrot” przy wyniku poniżej, aby uzupełnić.">
          <input value={rrn} onChange={(e) => setRrn(e.target.value)} pattern="[0-9]{12}" inputMode="numeric" required />
        </Pole>
        <div className="siatka-2">
          <Pole etykieta="Kwota zwrotu" podpowiedz="Całość albo część. Suma zwrotów nie może przekroczyć autoryzacji.">
            <input value={kwotaTekst} onChange={(e) => setKwota(e.target.value)} inputMode="decimal" required />
          </Pole>
          <Pole etykieta="Waluta" podpowiedz="Taka jak w autoryzacji">
            <input value={waluta} onChange={(e) => setWaluta(e.target.value.toUpperCase())} pattern="[A-Z]{3}" maxLength={3} />
          </Pole>
        </div>
        <Pole etykieta="ID żądania (idZadania)">
          <input value={idZadania} onChange={(e) => setIdZadania(e.target.value)} required spellCheck={false} />
        </Pole>
        <button type="submit" className="przycisk przycisk-glowny" disabled={trwa || !kluczApi}>
          {trwa ? 'Zwrot…' : 'Zwróć'}
        </button>
      </form>
      <BladApi blad={blad} />
      {wynik && <WynikDecyzji w={wynik} />}
    </section>
  );
}

/* ---------------- Test obciążenia (6.2): N równoległych autoryzacji ---------------- */

interface WynikTestu {
  n: number;
  karta: string;
  calkowityMs: number;
  sredniMs: number;
  maxMs: number;
  zatwierdzone: number;
  odrzucone: number;
  bledy: number;
  przykladBledu?: string;
}

function TestObciazenia({ kluczApi }: { kluczApi: string }) {
  const [n, setN] = useState(10);
  const [numer, setNumer] = useState(KARTY_TESTOWE[0].numer);
  const [trwa, setTrwa] = useState(false);
  const [wyniki, setWyniki] = useState<WynikTestu[]>([]);

  async function uruchom() {
    setTrwa(true);
    const start = performance.now();
    const proby = await Promise.all(
      Array.from({ length: n }, async () => {
        const t0 = performance.now();
        try {
          const { dane } = await api.sklep<WynikPlatnosci>('POST', '/platnosci/autoryzacje', kluczApi, {
            idZadania: noweIdZadania('test'),
            numerKarty: numer,
            kwota: 100,
            waluta: 'PLN',
          });
          return { ms: performance.now() - t0, decyzja: dane.decyzja as string, blad: undefined };
        } catch (e) {
          return { ms: performance.now() - t0, decyzja: 'BLAD', blad: opisBledu(jakoApiError(e)) };
        }
      }),
    );
    const calkowityMs = performance.now() - start;
    setWyniki((w) => [
      {
        n,
        karta: `${numer.slice(0, 4)}…${numer.slice(-4)}`,
        calkowityMs: Math.round(calkowityMs),
        sredniMs: Math.round(proby.reduce((s, p) => s + p.ms, 0) / proby.length),
        maxMs: Math.round(Math.max(...proby.map((p) => p.ms))),
        zatwierdzone: proby.filter((p) => p.decyzja === 'ZATWIERDZONA').length,
        odrzucone: proby.filter((p) => p.decyzja === 'ODRZUCONA').length,
        bledy: proby.filter((p) => p.decyzja === 'BLAD').length,
        przykladBledu: proby.find((p) => p.blad)?.blad,
      },
      ...w,
    ]);
    setTrwa(false);
  }

  return (
    <section className="panel">
      <div className="panel-naglowek">
        <h2>
          <Lightning aria-hidden /> Test obciążenia
        </h2>
      </div>
      <p className="wyciszony maly">
        Wysyła N równoległych autoryzacji po 1,00 PLN i mierzy czas (wymaganie NFR2: 100 równoległych autoryzacji bez
        błędów, średnio poniżej 1 s). Każda autoryzacja obciąża kartę, więc używaj kart demo.
      </p>
      <p className="wyciszony maly">
        Przeglądarka wysyła do jednego serwera najwyżej 6 żądań naraz, więc przy 100 zapytaniach reszta czeka w kolejce
        i czasy są wyższe niż w samej bramce. Pomiar NFR2 bez tego narzutu jest w <code>docs/wyniki-testow.md</code>.
      </p>
      <div className="filtry">
        <div className="segment" role="group" aria-label="Liczba zapytań">
          {[1, 10, 100].map((v) => (
            <button key={v} type="button" className={n === v ? 'aktywny' : undefined} aria-pressed={n === v} onClick={() => setN(v)}>
              {v}
            </button>
          ))}
        </div>
        <select value={numer} onChange={(e) => setNumer(e.target.value)} aria-label="Karta testowa">
          {KARTY_TESTOWE.map((k) => (
            <option key={k.numer} value={k.numer}>
              {k.siec} {k.numer.slice(0, 4)}…{k.numer.slice(-4)}
            </option>
          ))}
        </select>
        <button type="button" className="przycisk przycisk-glowny" disabled={trwa || !kluczApi} onClick={() => void uruchom()}>
          {trwa ? `Wysyłanie ${n}…` : `Wyślij ${n} ${n === 1 ? 'zapytanie' : 'zapytań'}`}
        </button>
      </div>

      {wyniki.length > 0 && (
        <div className="tabela-kontener">
          <table className="tabela">
            <thead>
              <tr>
                <th className="liczba">Zapytań</th>
                <th>Karta</th>
                <th className="liczba">Czas całkowity</th>
                <th className="liczba">Średni czas</th>
                <th className="liczba">Maks. czas</th>
                <th className="liczba">Zatwierdzone</th>
                <th className="liczba">Odrzucone</th>
                <th className="liczba">Błędy</th>
              </tr>
            </thead>
            <tbody>
              {wyniki.map((w, i) => (
                <tr key={i}>
                  <td className="liczba">{w.n}</td>
                  <td>
                    <code>{w.karta}</code>
                  </td>
                  <td className="liczba">{w.calkowityMs} ms</td>
                  <td className="liczba">{w.sredniMs} ms</td>
                  <td className="liczba">{w.maxMs} ms</td>
                  <td className="liczba">{w.zatwierdzone}</td>
                  <td className="liczba">{w.odrzucone}</td>
                  <td className={`liczba${w.bledy ? ' tekst-blad' : ''}`} title={w.przykladBledu}>
                    {w.bledy}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
