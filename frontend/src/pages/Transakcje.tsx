import { useEffect, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { CaretDown, CaretUp, FunnelSimple, X } from '@phosphor-icons/react';
import { api } from '../api/client';
import { SIECI, type Strona, type Transakcja, type TransakcjaSzczegoly } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import {
  BanerDanych,
  BladApi,
  CzasOdpowiedzi,
  Ladowanie,
  Modal,
  NaglowekStrony,
  Paginacja,
  Pole,
  Pusto,
  Siec,
  Status,
  Szczegoly,
} from '../components/ui';
import { data, groszeNaTekst, koniecDnia, kwota, nazwaSieci, naGrosze, poczatekDnia } from '../lib/format';

const ROZMIAR = 20;
const POLA_FILTRA = ['siec', 'idKarty', 'idSklepu', 'rrn', 'status', 'typ', 'od', 'do', 'kwotaOd', 'kwotaDo'] as const;
type PoleFiltra = (typeof POLA_FILTRA)[number];

/** Filtry są w adresie strony (?idSklepu=1&status=ODRZUCONA), więc da się je podlinkować i wrócić przyciskiem Wstecz. */
export function Transakcje() {
  const { czyAdmin, siec } = useAuth();
  const [params, setParams] = useSearchParams();
  const [wybrana, setWybrana] = useState<number | null>(null);

  const filtr = Object.fromEntries(POLA_FILTRA.map((p) => [p, params.get(p) ?? ''])) as Record<PoleFiltra, string>;
  const sortuj = params.get('sortuj') ?? 'dataTransakcji';
  const kierunek = params.get('kierunek') ?? 'desc';
  const strona = Number(params.get('strona') ?? 0);

  const [roboczy, setRoboczy] = useState(filtr);
  const adres = params.toString();
  useEffect(() => {
    setRoboczy(Object.fromEntries(POLA_FILTRA.map((p) => [p, params.get(p) ?? ''])) as Record<PoleFiltra, string>);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [adres]);

  const zapytanie = {
    siec: filtr.siec,
    idKarty: filtr.idKarty,
    idSklepu: filtr.idSklepu,
    rrn: filtr.rrn,
    status: filtr.status,
    typ: filtr.typ,
    od: filtr.od ? poczatekDnia(filtr.od) : undefined,
    do: filtr.do ? koniecDnia(filtr.do) : undefined,
    kwotaOd: filtr.kwotaOd ? (naGrosze(filtr.kwotaOd) ?? undefined) : undefined,
    kwotaDo: filtr.kwotaDo ? (naGrosze(filtr.kwotaDo) ?? undefined) : undefined,
    sortuj,
    kierunek,
    strona,
    rozmiar: ROZMIAR,
  };

  const q = useQuery({
    queryKey: ['transakcje', zapytanie],
    queryFn: () => api.get<Strona<Transakcja>>('/transakcje', zapytanie),
    placeholderData: keepPreviousData,
  });
  const dane = q.data?.dane;

  function ustaw(zmiany: Record<string, string | number | undefined>) {
    const p = new URLSearchParams(params);
    for (const [k, v] of Object.entries(zmiany)) {
      if (v === undefined || v === '' || (k === 'strona' && v === 0)) p.delete(k);
      else p.set(k, String(v));
    }
    setParams(p);
  }

  function zastosuj(e: FormEvent) {
    e.preventDefault();
    ustaw({ ...roboczy, strona: 0 });
  }

  function wyczysc() {
    const pusty = Object.fromEntries(POLA_FILTRA.map((p) => [p, ''])) as Record<PoleFiltra, string>;
    setRoboczy(pusty);
    setParams(new URLSearchParams());
  }

  function sortujPo(pole: 'dataTransakcji' | 'kwota') {
    const nowyKierunek = sortuj === pole && kierunek === 'desc' ? 'asc' : 'desc';
    ustaw({ sortuj: pole, kierunek: nowyKierunek, strona: 0 });
  }

  const zmien = (k: PoleFiltra) => (e: { target: { value: string } }) => setRoboczy({ ...roboczy, [k]: e.target.value });
  const aktywneFiltry = POLA_FILTRA.filter((p) => filtr[p]).length;

  return (
    <>
      <NaglowekStrony
        tytul="Transakcje"
        opis={
          czyAdmin
            ? 'Bez filtra sieci bramka przeszukuje wszystkie trzy węzły i łączy wyniki (widok globalny UNION ALL).'
            : `Transakcje sieci ${nazwaSieci(siec)}.`
        }
      />

      <form className="panel filtry-zaawansowane" onSubmit={zastosuj}>
        <div className="siatka-filtrow">
          {czyAdmin && (
            <Pole etykieta="Sieć">
              <select value={roboczy.siec} onChange={zmien('siec')}>
                <option value="">Wszystkie (3 węzły)</option>
                {SIECI.map((s) => (
                  <option key={s} value={s}>
                    {nazwaSieci(s)}
                  </option>
                ))}
              </select>
            </Pole>
          )}
          <Pole etykieta="Status">
            <select value={roboczy.status} onChange={zmien('status')}>
              <option value="">Wszystkie</option>
              <option value="AUTORYZOWANA">AUTORYZOWANA</option>
              <option value="ODRZUCONA">ODRZUCONA</option>
              <option value="ZWROCONA">ZWROCONA</option>
            </select>
          </Pole>
          <Pole etykieta="Typ">
            <select value={roboczy.typ} onChange={zmien('typ')}>
              <option value="">Wszystkie</option>
              <option value="AUTORYZACJA">AUTORYZACJA</option>
              <option value="ZWROT">ZWROT</option>
            </select>
          </Pole>
          <Pole etykieta="RRN">
            <input value={roboczy.rrn} onChange={zmien('rrn')} pattern="[0-9]{12}" placeholder="12 cyfr" inputMode="numeric" />
          </Pole>
          <Pole etykieta="ID karty">
            <input type="number" min={1} value={roboczy.idKarty} onChange={zmien('idKarty')} />
          </Pole>
          <Pole etykieta="ID sklepu">
            <input type="number" min={1} value={roboczy.idSklepu} onChange={zmien('idSklepu')} />
          </Pole>
          <Pole etykieta="Data od">
            <input type="date" value={roboczy.od} onChange={zmien('od')} />
          </Pole>
          <Pole etykieta="Data do (włącznie)">
            <input type="date" value={roboczy.do} onChange={zmien('do')} />
          </Pole>
          <Pole etykieta="Kwota od (PLN)">
            <input value={roboczy.kwotaOd} onChange={zmien('kwotaOd')} inputMode="decimal" placeholder="0,00" />
          </Pole>
          <Pole etykieta="Kwota do (PLN)">
            <input value={roboczy.kwotaDo} onChange={zmien('kwotaDo')} inputMode="decimal" placeholder="0,00" />
          </Pole>
        </div>
        <div className="akcje">
          <button type="submit" className="przycisk przycisk-glowny">
            <FunnelSimple aria-hidden /> Filtruj
          </button>
          {aktywneFiltry > 0 && (
            <button type="button" className="przycisk" onClick={wyczysc}>
              <X aria-hidden /> Wyczyść ({aktywneFiltry})
            </button>
          )}
          <CzasOdpowiedzi ms={q.data?.ms} laduje={q.isFetching} />
        </div>
      </form>

      <BanerDanych dane={dane} />
      <BladApi blad={q.error} />

      {q.isLoading ? (
        <Ladowanie />
      ) : dane && dane.elementy.length === 0 ? (
        <Pusto>Brak transakcji spełniających kryteria.</Pusto>
      ) : (
        dane && (
          <div className="tabela-kontener">
            <table className="tabela">
              <thead>
                <tr>
                  <th>ID</th>
                  <NaglowekSortowany pole="dataTransakcji" etykieta="Data" sortuj={sortuj} kierunek={kierunek} onSortuj={sortujPo} />
                  <th>RRN</th>
                  <th>Sieć</th>
                  <th>Karta</th>
                  <th>Sklep</th>
                  <th>Typ</th>
                  <NaglowekSortowany
                    pole="kwota"
                    etykieta="Kwota"
                    sortuj={sortuj}
                    kierunek={kierunek}
                    onSortuj={sortujPo}
                    liczba
                  />
                  <th>Status</th>
                  <th>Kod</th>
                </tr>
              </thead>
              <tbody>
                {dane.elementy.map((t) => (
                  <tr key={t.id} className="wiersz-klikalny" onClick={() => setWybrana(t.id)}>
                    <td className="liczba">{t.id}</td>
                    <td>{data(t.dataTransakcji)}</td>
                    <td>
                      <button
                        type="button"
                        className="link"
                        onClick={(e) => {
                          e.stopPropagation();
                          setWybrana(t.id);
                        }}
                      >
                        <code>{t.rrn}</code>
                      </button>
                    </td>
                    <td>
                      <Siec kod={t.siec} />
                    </td>
                    <td>
                      <code>{t.numerKarty ?? `#${t.idKarty}`}</code>
                    </td>
                    <td>#{t.idSklepu}</td>
                    <td>
                      <Status wartosc={t.typ === 'ZWROT' ? 'ZWROT' : null} />
                      {t.typ === 'AUTORYZACJA' && <span className="wyciszony">autoryzacja</span>}
                    </td>
                    <td className="liczba">{kwota(t.kwota, t.waluta)}</td>
                    <td>
                      <Status wartosc={t.status} />
                    </td>
                    <td title={t.komunikat}>
                      <code>{t.kod}</code>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )
      )}
      {dane && (
        <Paginacja strona={strona} rozmiar={ROZMIAR} lacznie={dane.lacznie} onStrona={(s) => ustaw({ strona: s })} />
      )}

      {wybrana !== null && <SzczegolyTransakcji id={wybrana} onZamknij={() => setWybrana(null)} onPokaz={setWybrana} />}
    </>
  );
}

function NaglowekSortowany({
  pole,
  etykieta,
  sortuj,
  kierunek,
  onSortuj,
  liczba,
}: {
  pole: 'dataTransakcji' | 'kwota';
  etykieta: string;
  sortuj: string;
  kierunek: string;
  onSortuj: (p: 'dataTransakcji' | 'kwota') => void;
  liczba?: boolean;
}) {
  const aktywne = sortuj === pole;
  return (
    <th
      className={liczba ? 'liczba' : undefined}
      aria-sort={aktywne ? (kierunek === 'asc' ? 'ascending' : 'descending') : 'none'}
    >
      <button type="button" className="sortowanie" onClick={() => onSortuj(pole)}>
        {etykieta}
        {aktywne ? (
          kierunek === 'asc' ? (
            <CaretUp aria-hidden weight="bold" />
          ) : (
            <CaretDown aria-hidden weight="bold" />
          )
        ) : (
          <CaretDown aria-hidden className="wyciszony" />
        )}
      </button>
    </th>
  );
}

function SzczegolyTransakcji({
  id,
  onZamknij,
  onPokaz,
}: {
  id: number;
  onZamknij: () => void;
  onPokaz: (id: number) => void;
}) {
  const q = useQuery({
    queryKey: ['transakcje', 'szczegoly', id],
    queryFn: () => api.get<TransakcjaSzczegoly>(`/transakcje/${id}`),
  });
  const d = q.data?.dane;
  const t = d?.transakcja;
  const sumaZwrotow = d?.zwroty.reduce((s, z) => s + (z.status === 'ODRZUCONA' ? 0 : z.kwota), 0) ?? 0;

  return (
    <Modal tytul={`Transakcja #${id}`} onZamknij={onZamknij} szeroki>
      <BladApi blad={q.error} />
      {q.isLoading && <Ladowanie />}
      {t && (
        <>
          <Szczegoly
            wiersze={[
              ['RRN', <code key="r">{t.rrn}</code>],
              ['Typ', t.typ],
              ['Status', <Status key="s" wartosc={t.status} />],
              ['Kwota', <strong key="k">{kwota(t.kwota, t.waluta)}</strong>],
              ['Data', data(t.dataTransakcji)],
              ['Sieć (węzeł)', <Siec key="n" kod={t.siec} />],
              ['Karta', <code key="c">{t.numerKarty ?? `#${t.idKarty}`}</code>],
              ['Sklep', <Link key="sk" to={`/transakcje?idSklepu=${t.idSklepu}`} onClick={onZamknij}>#{t.idSklepu}</Link>],
              ['Kod odpowiedzi', `${t.kod} – ${t.komunikat}`],
              ['ID żądania sklepu', <code key="z">{t.idZadania}</code>],
            ]}
          />
          {d.pierwotna && (
            <section className="sekcja-modalu">
              <h3>Transakcja pierwotna (zwrot dotyczy tej autoryzacji)</h3>
              <TabelaPowiazanych lista={[d.pierwotna]} onPokaz={onPokaz} />
            </section>
          )}
          {t.typ === 'AUTORYZACJA' && (
            <section className="sekcja-modalu">
              <h3>
                Zwroty {d.zwroty.length > 0 && <span className="wyciszony">(razem {kwota(sumaZwrotow, t.waluta)} z {groszeNaTekst(t.kwota)})</span>}
              </h3>
              {d.zwroty.length === 0 ? (
                <p className="wyciszony">Brak zwrotów.</p>
              ) : (
                <TabelaPowiazanych lista={d.zwroty} onPokaz={onPokaz} />
              )}
            </section>
          )}
        </>
      )}
    </Modal>
  );
}

function TabelaPowiazanych({ lista, onPokaz }: { lista: Transakcja[]; onPokaz: (id: number) => void }) {
  return (
    <div className="tabela-kontener">
      <table className="tabela">
        <thead>
          <tr>
            <th>RRN</th>
            <th>Typ</th>
            <th>Data</th>
            <th className="liczba">Kwota</th>
            <th>Status</th>
          </tr>
        </thead>
        <tbody>
          {lista.map((z) => (
            <tr key={z.id}>
              <td>
                <button type="button" className="link" onClick={() => onPokaz(z.id)}>
                  <code>{z.rrn}</code>
                </button>
              </td>
              <td>{z.typ}</td>
              <td>{data(z.dataTransakcji)}</td>
              <td className="liczba">{kwota(z.kwota, z.waluta)}</td>
              <td>
                <Status wartosc={z.status} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
