import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { CheckCircle, Database, Pause, Play, WarningCircle, XCircle } from '@phosphor-icons/react';
import { api } from '../api/client';
import type { KodSieci, StanWezla, StanWezlow } from '../api/types';
import { BladApi, CzasOdpowiedzi, Ladowanie, NaglowekStrony, Siec } from '../components/ui';
import { data, nazwaSieci } from '../lib/format';

const INTERWAL_MS = 5000;
const DLUGOSC_HISTORII = 36; // 36 × 5 s = 3 minuty

interface Probka {
  czas: string;
  dostepny: boolean;
  ms: number | null;
}

// Historia trzymana poza komponentem, żeby nie znikała po przejściu na inną stronę panelu.
const historia: Partial<Record<KodSieci, Probka[]>> = {};

function dopisz(stan: StanWezlow) {
  for (const w of stan.wezly) {
    const lista = historia[w.siec] ?? [];
    if (lista[lista.length - 1]?.czas === stan.czasPomiaru) continue;
    lista.push({ czas: stan.czasPomiaru, dostepny: w.dostepny, ms: w.czasOdpowiedziMs ?? null });
    historia[w.siec] = lista.slice(-DLUGOSC_HISTORII);
  }
}

export function StanWezlowStrona() {
  const [wstrzymane, setWstrzymane] = useState(false);
  const q = useQuery({
    queryKey: ['wezly'],
    queryFn: () => api.get<StanWezlow>('/wezly/stan'),
    refetchInterval: wstrzymane ? false : INTERWAL_MS,
  });
  const stan = q.data?.dane;

  useEffect(() => {
    if (stan) dopisz(stan);
  }, [stan]);

  const dostepne = stan?.wezly.filter((w) => w.dostepny).length ?? 0;

  return (
    <>
      <NaglowekStrony
        tytul="Stan węzłów"
        opis={`FR11 · dostępność węzłów i opóźnienie replik, odświeżane co ${INTERWAL_MS / 1000} s.`}
        akcje={
          <button type="button" className="przycisk" onClick={() => setWstrzymane((w) => !w)} aria-pressed={wstrzymane}>
            {wstrzymane ? <Play aria-hidden /> : <Pause aria-hidden />}
            {wstrzymane ? 'Wznów odświeżanie' : 'Wstrzymaj odświeżanie'}
          </button>
        }
      />

      <BladApi blad={q.error} />
      {q.isLoading && <Ladowanie />}

      {stan && (
        <>
          <div className={`podsumowanie-wezlow ${dostepne === stan.wezly.length ? 'ok' : 'blad'}`} role="status">
            {dostepne === stan.wezly.length ? <CheckCircle aria-hidden weight="fill" /> : <WarningCircle aria-hidden weight="fill" />}
            <div>
              <strong>
                {dostepne} z {stan.wezly.length} węzłów dostępnych
              </strong>
              <div className="maly">
                Pomiar: {data(stan.czasPomiaru)} · <CzasOdpowiedzi ms={q.data?.ms} laduje={q.isFetching} />
              </div>
            </div>
          </div>

          <div className="siatka-wezlow">
            {stan.wezly.map((w) => (
              <KartaWezla key={w.siec} w={w} />
            ))}
          </div>

          <section className="panel">
            <h2>Historia dostępności (ostatnie 3 minuty)</h2>
            <p className="wyciszony maly">
              Każdy kwadrat to jeden pomiar co 5 s. Przydaje się w teście awarii: widać moment wyłączenia węzła i jego
              powrót.
            </p>
            <div className="historia">
              {stan.wezly.map((w) => (
                <div key={w.siec} className="historia-wiersz">
                  <span className="historia-etykieta">{nazwaSieci(w.siec)}</span>
                  <span className="historia-probki">
                    {(historia[w.siec] ?? []).map((p) => (
                      <span
                        key={p.czas}
                        className={`probka ${p.dostepny ? 'probka-ok' : 'probka-blad'}`}
                        title={`${data(p.czas)}: ${p.dostepny ? `dostępny, ${p.ms ?? '?'} ms` : 'niedostępny'}`}
                      />
                    ))}
                  </span>
                </div>
              ))}
            </div>
          </section>

          <section className="panel podpowiedz-testu">
            <h2>Jak przeprowadzić test awarii (6.4)</h2>
            <ol>
              <li>
                Wyłącz węzeł: <code>docker stop mastercard-db</code>. Po kilku sekundach karta MasterCard zmieni się na
                „niedostępny”.
              </li>
              <li>
                Sprawdź listy kart i transakcji (baner „dane częściowe”), płatność kartą Visa w symulatorze (działa) i
                kartą MasterCard (kod 91), blokadę globalną klienta (wycofana, nic nie zmienione).
              </li>
              <li>
                Uruchom węzeł ponownie: <code>docker start mastercard-db</code> i sprawdź, czy repliki się odświeżyły, a
                licznik wiszących transakcji 2PC wrócił do zera.
              </li>
            </ol>
          </section>
        </>
      )}
    </>
  );
}

function KartaWezla({ w }: { w: StanWezla }) {
  const r = w.replikacja;
  return (
    <article className={`karta-wezla ${w.dostepny ? '' : 'karta-wezla-blad'}`}>
      <header>
        <div className="karta-wezla-tytul">
          <Database aria-hidden size={20} />
          <div>
            <strong>
              Węzeł {w.nrWezla} · <Siec kod={w.siec} />
            </strong>
            <div className="maly wyciszony">{w.rola === 'GLOWNY' ? 'kopia główna danych wspólnych' : 'replika (widoki zmaterializowane)'}</div>
          </div>
        </div>
        <span className={`stan ${w.dostepny ? 'stan-ok' : 'stan-blad'}`}>
          {w.dostepny ? <CheckCircle aria-hidden weight="fill" /> : <XCircle aria-hidden weight="fill" />}
          {w.dostepny ? 'dostępny' : 'niedostępny'}
        </span>
      </header>

      <dl className="metryki">
        <div>
          <dt>Czas odpowiedzi</dt>
          <dd>{w.czasOdpowiedziMs != null ? `${w.czasOdpowiedziMs} ms` : '—'}</dd>
        </div>
        <div>
          <dt>Wiszące transakcje 2PC</dt>
          <dd className={w.wiszaceTransakcje2pc ? 'tekst-ostrzezenie' : undefined}>{w.wiszaceTransakcje2pc ?? '—'}</dd>
        </div>
        {r && (
          <>
            <div>
              <dt>Opóźnienie repliki</dt>
              <dd className={r.przekroczonoLimit ? 'tekst-blad' : undefined}>
                {r.opoznienieSekund != null ? `${r.opoznienieSekund} s` : '—'}
                {r.przekroczonoLimit && ' (ponad 60 s)'}
              </dd>
            </div>
            <div>
              <dt>Ostatnie odświeżenie</dt>
              <dd>{data(r.ostatnieOdswiezenie)}</dd>
            </div>
          </>
        )}
      </dl>

      {r?.ostatniBlad && (
        <div className="baner baner-ostrzezenie maly">
          Ostatni błąd odświeżania ({data(r.ostatniBlad.czas)}): {r.ostatniBlad.komunikat}
        </div>
      )}
      {w.blad && <div className="baner baner-blad maly">{w.blad}</div>}

      {w.logiMv && w.logiMv.length > 0 && (
        <div className="logi-mv">
          <div className="maly wyciszony">Logi widoków zmaterializowanych (zmiany czekające na repliki)</div>
          <table className="tabela tabela-zwarta">
            <thead>
              <tr>
                <th>Tabela</th>
                <th className="liczba">Wiersze</th>
                <th className="liczba">Rozmiar</th>
              </tr>
            </thead>
            <tbody>
              {w.logiMv.map((l) => (
                <tr key={l.tabela}>
                  <td>{l.tabela}</td>
                  <td className="liczba">{l.liczbaWierszy}</td>
                  <td className="liczba">{(l.rozmiarBajtow / 1024).toFixed(0)} KB</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </article>
  );
}
