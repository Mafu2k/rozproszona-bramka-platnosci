import { useEffect, useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ChartBar } from '@phosphor-icons/react';
import { api } from '../api/client';
import { SIECI, type KodSieci, type RaportObrotow, type Sklep, type StatystykiSieci } from '../api/types';
import { BanerDanych, BladApi, CzasOdpowiedzi, Ladowanie, NaglowekStrony, Pole, Siec } from '../components/ui';
import { WyborSklepu } from '../components/Wybor';
import { WykresSlupkowy } from '../components/Wykres';
import { dzien, dzisiaj, koniecDnia, kwota, poczatekDnia } from '../lib/format';

function Okres({
  od,
  doo,
  onOd,
  onDo,
}: {
  od: string;
  doo: string;
  onOd: (v: string) => void;
  onDo: (v: string) => void;
}) {
  return (
    <>
      <Pole etykieta="Od">
        <input type="date" value={od} onChange={(e) => onOd(e.target.value)} required />
      </Pole>
      <Pole etykieta="Do (włącznie)">
        <input type="date" value={doo} onChange={(e) => onDo(e.target.value)} required min={od} />
      </Pole>
    </>
  );
}

function BrakSieci({ siec }: { siec: KodSieci }) {
  return (
    <tr className="wiersz-brak">
      <td>
        <Siec kod={siec} />
      </td>
      <td colSpan={5} className="wyciszony">
        brak danych – węzeł niedostępny
      </td>
    </tr>
  );
}

/* ---------------- FR9: raport obrotów sklepu (scenariusz 3) ---------------- */

export function RaportObrotowSklepu() {
  const [params, setParams] = useSearchParams();
  const idZAdresu = Number(params.get('idSklepu')) || null;
  const [sklep, setSklep] = useState<Sklep | null>(null);
  const [od, setOd] = useState(params.get('od') ?? dzisiaj(-30));
  const [doo, setDo] = useState(params.get('do') ?? dzisiaj());

  // Sklep podany w adresie (np. link „Raport” z listy sklepów) – wczytujemy jego dane do pola wyboru.
  const qSklep = useQuery({
    queryKey: ['sklepy', 'szczegoly', idZAdresu],
    queryFn: () => api.get<Sklep>(`/sklepy/${idZAdresu}`),
    enabled: idZAdresu !== null && sklep === null,
  });
  useEffect(() => {
    if (qSklep.data && !sklep) setSklep(qSklep.data.dane);
  }, [qSklep.data, sklep]);

  const wykonany = params.get('idSklepu') && params.get('od') && params.get('do');
  const zapytanie = wykonany
    ? { idSklepu: params.get('idSklepu')!, od: poczatekDnia(params.get('od')!), do: koniecDnia(params.get('do')!) }
    : null;

  const q = useQuery({
    queryKey: ['raporty', 'obroty', zapytanie],
    queryFn: () => api.get<RaportObrotow>('/raporty/obroty-sklepu', zapytanie!),
    enabled: zapytanie !== null,
  });
  const r = q.data?.dane;

  function generuj(e: FormEvent) {
    e.preventDefault();
    if (!sklep) return;
    setParams({ idSklepu: String(sklep.id), od, do: doo });
  }

  return (
    <>
      <NaglowekStrony
        tytul="Raport obrotów sklepu"
        opis="FR9 · zapytanie rozproszone: bramka odpytuje trzy węzły, a potem łączy i sumuje wyniki."
      />

      <form className="panel filtry-zaawansowane" onSubmit={generuj}>
        <div className="siatka-filtrow">
          <div className="pole-szerokie">
            <Pole etykieta="Sklep">
              <WyborSklepu wartosc={sklep} onZmiana={setSklep} />
            </Pole>
          </div>
          <Okres od={od} doo={doo} onOd={setOd} onDo={setDo} />
        </div>
        <div className="akcje">
          <button type="submit" className="przycisk przycisk-glowny" disabled={!sklep || q.isFetching}>
            <ChartBar aria-hidden /> {q.isFetching ? 'Generowanie…' : 'Generuj raport'}
          </button>
          <CzasOdpowiedzi ms={q.data?.ms} laduje={q.isFetching} />
        </div>
      </form>

      <BladApi blad={q.error} />
      {q.isLoading && zapytanie && <Ladowanie />}

      {r && (
        <section className="panel">
          <div className="panel-naglowek">
            <h2>{r.nazwaSklepu}</h2>
            <span className="wyciszony">
              {dzien(r.od)} – {dzien(new Date(new Date(r.do).getTime() - 1).toISOString())}
            </span>
          </div>
          <BanerDanych dane={r} />

          <div className="tabela-kontener">
            <table className="tabela">
              <thead>
                <tr>
                  <th>Sieć (węzeł)</th>
                  <th className="liczba">Autoryzacje</th>
                  <th className="liczba">Suma autoryzacji</th>
                  <th className="liczba">Zwroty</th>
                  <th className="liczba">Suma zwrotów</th>
                  <th className="liczba">Obrót netto</th>
                </tr>
              </thead>
              <tbody>
                {SIECI.map((s) => {
                  const w = r.sieci.find((x) => x.siec === s);
                  if (!w) {
                    return r.brakujaceSieci.includes(s) ? (
                      <BrakSieci key={s} siec={s} />
                    ) : (
                      <tr key={s}>
                        <td>
                          <Siec kod={s} />
                        </td>
                        <td className="liczba">0</td>
                        <td className="liczba">{kwota(0)}</td>
                        <td className="liczba">0</td>
                        <td className="liczba">{kwota(0)}</td>
                        <td className="liczba">{kwota(0)}</td>
                      </tr>
                    );
                  }
                  return (
                    <tr key={s}>
                      <td>
                        <Siec kod={s} />
                      </td>
                      <td className="liczba">{w.liczbaAutoryzacji}</td>
                      <td className="liczba">{kwota(w.sumaAutoryzacji)}</td>
                      <td className="liczba">{w.liczbaZwrotow}</td>
                      <td className="liczba">{kwota(w.sumaZwrotow)}</td>
                      <td className="liczba">{kwota(w.sumaAutoryzacji - w.sumaZwrotow)}</td>
                    </tr>
                  );
                })}
              </tbody>
              <tfoot>
                <tr>
                  <th>Łącznie{r.czesciowy && ' (częściowo)'}</th>
                  <td className="liczba">{r.lacznie.liczbaAutoryzacji}</td>
                  <td className="liczba">{kwota(r.lacznie.sumaAutoryzacji)}</td>
                  <td className="liczba">{r.lacznie.liczbaZwrotow}</td>
                  <td className="liczba">{kwota(r.lacznie.sumaZwrotow)}</td>
                  <td className="liczba">{kwota(r.lacznie.sumaAutoryzacji - r.lacznie.sumaZwrotow)}</td>
                </tr>
              </tfoot>
            </table>
          </div>

          <WykresSlupkowy
            tytul="Suma autoryzacji według sieci"
            slupki={r.sieci.map((w) => ({ siec: w.siec ?? '?', wartosc: w.sumaAutoryzacji, tekst: kwota(w.sumaAutoryzacji) }))}
          />
        </section>
      )}
    </>
  );
}

/* ---------------- FR10: statystyki sieci ---------------- */

export function StatystykiSieciStrona() {
  const [od, setOd] = useState(dzisiaj(-30));
  const [doo, setDo] = useState(dzisiaj());
  const [okres, setOkres] = useState({ od: poczatekDnia(dzisiaj(-30)), do: koniecDnia(dzisiaj()) });

  const q = useQuery({
    queryKey: ['raporty', 'statystyki', okres],
    queryFn: () => api.get<StatystykiSieci>('/raporty/statystyki-sieci', okres),
  });
  const s = q.data?.dane;

  const razem = s?.sieci.reduce(
    (a, w) => ({
      liczba: a.liczba + w.liczbaTransakcji,
      suma: a.suma + w.sumaKwot,
      odrzucone: a.odrzucone + w.liczbaOdrzuconych,
    }),
    { liczba: 0, suma: 0, odrzucone: 0 },
  );

  function zastosuj(e: FormEvent) {
    e.preventDefault();
    setOkres({ od: poczatekDnia(od), do: koniecDnia(doo) });
  }

  return (
    <>
      <NaglowekStrony
        tytul="Statystyki sieci"
        opis="FR10 · agregacja COUNT, SUM i odsetek odrzuceń liczone na każdym węźle i zestawione razem."
      />

      <form className="panel filtry-zaawansowane" onSubmit={zastosuj}>
        <div className="siatka-filtrow">
          <Okres od={od} doo={doo} onOd={setOd} onDo={setDo} />
        </div>
        <div className="akcje">
          <button type="submit" className="przycisk przycisk-glowny" disabled={q.isFetching}>
            <ChartBar aria-hidden /> Pokaż
          </button>
          <CzasOdpowiedzi ms={q.data?.ms} laduje={q.isFetching} />
        </div>
      </form>

      <BladApi blad={q.error} />
      {q.isLoading && <Ladowanie />}

      {s && razem && (
        <>
          <BanerDanych dane={s} />
          <div className="kafelki">
            <div className="kafelek">
              <span className="kafelek-etykieta">Transakcje</span>
              <span className="kafelek-wartosc">{razem.liczba.toLocaleString('pl-PL')}</span>
            </div>
            <div className="kafelek">
              <span className="kafelek-etykieta">Suma zatwierdzonych</span>
              <span className="kafelek-wartosc">{kwota(razem.suma)}</span>
            </div>
            <div className="kafelek">
              <span className="kafelek-etykieta">Odrzucone</span>
              <span className="kafelek-wartosc">
                {razem.odrzucone.toLocaleString('pl-PL')}{' '}
                <span className="kafelek-dodatek">
                  ({razem.liczba > 0 ? ((razem.odrzucone / razem.liczba) * 100).toFixed(1) : '0.0'}%)
                </span>
              </span>
            </div>
          </div>

          <section className="panel">
            <div className="tabela-kontener">
              <table className="tabela">
                <thead>
                  <tr>
                    <th>Sieć (węzeł)</th>
                    <th className="liczba">Liczba transakcji</th>
                    <th className="liczba">Suma zatwierdzonych</th>
                    <th className="liczba">Odrzucone</th>
                    <th className="liczba">Odsetek odrzuceń</th>
                  </tr>
                </thead>
                <tbody>
                  {SIECI.map((kod) => {
                    const w = s.sieci.find((x) => x.siec === kod);
                    if (!w) {
                      return (
                        <tr key={kod} className="wiersz-brak">
                          <td>
                            <Siec kod={kod} />
                          </td>
                          <td colSpan={4} className="wyciszony">
                            {s.brakujaceSieci.includes(kod) ? 'brak danych – węzeł niedostępny' : 'brak transakcji'}
                          </td>
                        </tr>
                      );
                    }
                    return (
                      <tr key={kod}>
                        <td>
                          <Siec kod={kod} />
                        </td>
                        <td className="liczba">{w.liczbaTransakcji.toLocaleString('pl-PL')}</td>
                        <td className="liczba">{kwota(w.sumaKwot)}</td>
                        <td className="liczba">{w.liczbaOdrzuconych.toLocaleString('pl-PL')}</td>
                        <td className="liczba">{w.odsetekOdrzuconych.toFixed(1)}%</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </section>

          <div className="siatka-wykresow">
            <section className="panel">
              <WykresSlupkowy
                tytul="Liczba transakcji"
                slupki={s.sieci.map((w) => ({
                  siec: w.siec,
                  wartosc: w.liczbaTransakcji,
                  tekst: w.liczbaTransakcji.toLocaleString('pl-PL'),
                }))}
              />
            </section>
            <section className="panel">
              <WykresSlupkowy
                tytul="Suma zatwierdzonych autoryzacji"
                slupki={s.sieci.map((w) => ({ siec: w.siec, wartosc: w.sumaKwot, tekst: kwota(w.sumaKwot) }))}
              />
            </section>
            <section className="panel">
              <WykresSlupkowy
                tytul="Odsetek odrzuconych autoryzacji"
                slupki={s.sieci.map((w) => ({
                  siec: w.siec,
                  wartosc: w.odsetekOdrzuconych,
                  tekst: `${w.odsetekOdrzuconych.toFixed(1)}%`,
                }))}
              />
            </section>
          </div>
        </>
      )}
    </>
  );
}
