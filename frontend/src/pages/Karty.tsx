import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import { SIECI, type Karta, type KartaNowa, type KartaWydana, type Klient, type Strona } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { usePowiadomienie } from '../components/Powiadomienia';
import {
  BanerDanych,
  BladApi,
  CzasOdpowiedzi,
  JednorazowaWartosc,
  Ladowanie,
  Modal,
  NaglowekStrony,
  Paginacja,
  Pole,
  Potwierdzenie,
  Pusto,
  Siec,
  Status,
  Szczegoly,
} from '../components/ui';
import { WyborKlienta } from '../components/Wybor';
import { bledyPol } from '../lib/bledy';
import { data, groszeNaTekst, grupujNumer, kwota, nazwaSieci, naGrosze } from '../lib/format';
import { useZapis } from '../lib/useZapis';

const ROZMIAR = 20;

export function Karty() {
  const { czyAdmin, czyOperator, siec } = useAuth();
  const [filtr, setFiltr] = useState({ siec: '', idKlienta: '', status: '' });
  const [strona, setStrona] = useState(0);
  const [wydanie, setWydanie] = useState(false);

  const q = useQuery({
    queryKey: ['karty', filtr, strona],
    queryFn: () => api.get<Strona<Karta>>('/karty', { ...filtr, strona, rozmiar: ROZMIAR }),
    placeholderData: keepPreviousData,
  });
  const dane = q.data?.dane;

  const zmien = (k: keyof typeof filtr) => (e: { target: { value: string } }) => {
    setStrona(0);
    setFiltr({ ...filtr, [k]: e.target.value });
  };

  return (
    <>
      <NaglowekStrony
        tytul="Karty"
        opis={
          czyAdmin
            ? 'Karty ze wszystkich węzłów (fragmentacja pozioma według sieci). Wybierz sieć, aby odpytać jeden węzeł.'
            : `Karty sieci ${nazwaSieci(siec)} – dane z węzła Twojej sieci.`
        }
        akcje={
          czyOperator && (
            <button type="button" className="przycisk przycisk-glowny" onClick={() => setWydanie(true)}>
              + Wydaj kartę
            </button>
          )
        }
      />

      <div className="filtry">
        {czyAdmin && (
          <select value={filtr.siec} onChange={zmien('siec')}>
            <option value="">Wszystkie sieci (3 węzły)</option>
            {SIECI.map((s) => (
              <option key={s} value={s}>
                {nazwaSieci(s)}
              </option>
            ))}
          </select>
        )}
        <input type="number" min={1} placeholder="ID klienta" value={filtr.idKlienta} onChange={zmien('idKlienta')} />
        <select value={filtr.status} onChange={zmien('status')}>
          <option value="">Wszystkie statusy</option>
          <option value="AKTYWNA">Aktywne</option>
          <option value="ZABLOKOWANA">Zablokowane</option>
        </select>
        <CzasOdpowiedzi ms={q.data?.ms} laduje={q.isFetching} />
      </div>

      <BanerDanych dane={dane} />
      <BladApi blad={q.error} />

      {q.isLoading ? (
        <Ladowanie />
      ) : dane && dane.elementy.length === 0 ? (
        <Pusto>Brak kart spełniających kryteria.</Pusto>
      ) : (
        dane && <TabelaKart karty={dane.elementy} />
      )}
      {dane && <Paginacja strona={strona} rozmiar={ROZMIAR} lacznie={dane.lacznie} onStrona={setStrona} />}

      {wydanie && <FormularzWydania onZamknij={() => setWydanie(false)} />}
    </>
  );
}

export function TabelaKart({ karty, bezKlienta }: { karty: Karta[]; bezKlienta?: boolean }) {
  return (
    <div className="tabela-kontener">
      <table className="tabela">
        <thead>
          <tr>
            <th>ID</th>
            <th>Numer</th>
            <th>Sieć</th>
            {!bezKlienta && <th>Klient</th>}
            <th>Ważna do</th>
            <th className="liczba">Limit</th>
            <th>Dostępne środki</th>
            <th>Status</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {karty.map((k) => (
            <tr key={k.id}>
              <td className="liczba">{k.id}</td>
              <td>
                <code>{k.numerKarty}</code>
              </td>
              <td>
                <Siec kod={k.siec} />
              </td>
              {!bezKlienta && (
                <td>
                  <Link to={`/klienci/${k.idKlienta}`}>#{k.idKlienta}</Link>
                </td>
              )}
              <td>{k.dataWaznosci}</td>
              <td className="liczba">{kwota(k.limitKredytowy, k.waluta)}</td>
              <td>
                <Srodki karta={k} />
              </td>
              <td>
                <Status wartosc={k.status} />
              </td>
              <td className="akcje-wiersza">
                <AkcjeKarty karta={k} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Srodki({ karta }: { karta: Karta }) {
  const procent = karta.limitKredytowy > 0 ? Math.round((karta.dostepneSrodki / karta.limitKredytowy) * 100) : 0;
  return (
    <div className="srodki" title={`${procent}% limitu`}>
      <span className="liczba">{kwota(karta.dostepneSrodki, karta.waluta)}</span>
      <span className="pasek">
        <span style={{ width: `${Math.min(100, Math.max(0, procent))}%` }} />
      </span>
    </div>
  );
}

/** Akcje operatora na karcie jego sieci: zmiana limitu, blokada, odblokowanie (FR6). */
export function AkcjeKarty({ karta }: { karta: Karta }) {
  const { czyOperator, siec } = useAuth();
  const queryClient = useQueryClient();
  const pokaz = usePowiadomienie();
  const [okno, setOkno] = useState<'limit' | 'blokada' | null>(null);

  const zmianaStatusu = useZapis((klucz, akcja: 'blokada' | 'odblokowanie') =>
    api.zapis<Karta>('POST', `/karty/${karta.id}/${akcja}`, klucz),
  );

  if (!czyOperator || karta.siec !== siec) return null;

  async function przelacz() {
    const akcja = karta.status === 'AKTYWNA' ? 'blokada' : 'odblokowanie';
    const wynik = await zmianaStatusu.wykonaj(akcja);
    if (wynik) {
      await queryClient.invalidateQueries({ queryKey: ['karty'] });
      await queryClient.invalidateQueries({ queryKey: ['klient-karty'] });
      pokaz(`Karta ${wynik.dane.numerKarty}: ${wynik.dane.status}`);
      setOkno(null);
    }
  }

  return (
    <>
      <button type="button" className="przycisk przycisk-maly" onClick={() => setOkno('limit')}>
        Limit
      </button>
      <button
        type="button"
        className={`przycisk przycisk-maly${karta.status === 'AKTYWNA' ? ' przycisk-niebezpieczny-tekst' : ''}`}
        onClick={() => {
          zmianaStatusu.wyczyscBlad();
          setOkno('blokada');
        }}
      >
        {karta.status === 'AKTYWNA' ? 'Zablokuj' : 'Odblokuj'}
      </button>

      {okno === 'blokada' && (
        <Potwierdzenie
          tytul={karta.status === 'AKTYWNA' ? 'Blokada karty' : 'Odblokowanie karty'}
          tresc={
            <p>
              {karta.status === 'AKTYWNA' ? 'Zablokować' : 'Odblokować'} kartę <code>{karta.numerKarty}</code>? Operacja
              wykona się tylko na węźle sieci {nazwaSieci(karta.siec)}.
            </p>
          }
          przycisk={karta.status === 'AKTYWNA' ? 'Zablokuj' : 'Odblokuj'}
          niebezpieczne={karta.status === 'AKTYWNA'}
          trwa={zmianaStatusu.trwa}
          blad={zmianaStatusu.blad}
          onTak={() => void przelacz()}
          onNie={() => setOkno(null)}
        />
      )}
      {okno === 'limit' && <ZmianaLimitu karta={karta} onZamknij={() => setOkno(null)} />}
    </>
  );
}

function ZmianaLimitu({ karta, onZamknij }: { karta: Karta; onZamknij: () => void }) {
  const queryClient = useQueryClient();
  const pokaz = usePowiadomienie();
  const [limit, setLimit] = useState(groszeNaTekst(karta.limitKredytowy));
  const [bladPola, setBladPola] = useState<string>();
  const zapis = useZapis((klucz, limitKredytowy: number) =>
    api.zapis<Karta>('PUT', `/karty/${karta.id}/limit`, klucz, { limitKredytowy }),
  );
  const wykorzystane = karta.limitKredytowy - karta.dostepneSrodki;

  async function wyslij(e: FormEvent) {
    e.preventDefault();
    const grosze = naGrosze(limit);
    if (grosze === null) return setBladPola('Podaj kwotę, np. 5000 albo 5000,00');
    setBladPola(undefined);
    const wynik = await zapis.wykonaj(grosze);
    if (wynik) {
      await queryClient.invalidateQueries({ queryKey: ['karty'] });
      await queryClient.invalidateQueries({ queryKey: ['klient-karty'] });
      pokaz(`Nowy limit karty ${wynik.dane.numerKarty}: ${kwota(wynik.dane.limitKredytowy, wynik.dane.waluta)}`);
      onZamknij();
    }
  }

  return (
    <Modal
      tytul="Zmiana limitu karty"
      onZamknij={onZamknij}
      stopka={
        <>
          <button type="button" className="przycisk" onClick={onZamknij}>
            Anuluj
          </button>
          <button type="submit" form="formularz-limitu" className="przycisk przycisk-glowny" disabled={zapis.trwa}>
            {zapis.trwa ? 'Zapisywanie…' : 'Zmień limit'}
          </button>
        </>
      }
    >
      <form id="formularz-limitu" className="formularz" onSubmit={wyslij}>
        <Szczegoly
          wiersze={[
            ['Karta', <code key="n">{karta.numerKarty}</code>],
            ['Obecny limit', kwota(karta.limitKredytowy, karta.waluta)],
            ['Wykorzystane', kwota(wykorzystane, karta.waluta)],
          ]}
        />
        <BladApi blad={zapis.blad} />
        <Pole
          etykieta={`Nowy limit (${karta.waluta})`}
          blad={bladPola ?? bledyPol(zapis.blad).limitKredytowy}
          podpowiedz={`Nie może być niższy niż wykorzystana kwota (${kwota(wykorzystane, karta.waluta)}).`}
        >
          <input value={limit} onChange={(e) => setLimit(e.target.value)} inputMode="decimal" required autoFocus />
        </Pole>
      </form>
    </Modal>
  );
}

/** Wydanie karty klientowi w sieci operatora (POST /karty). Pełny numer pokazujemy tylko raz. */
export function FormularzWydania({ klient: poczatkowy, onZamknij }: { klient?: Klient; onZamknij: () => void }) {
  const { siec } = useAuth();
  const queryClient = useQueryClient();
  const [klient, setKlient] = useState<Klient | null>(poczatkowy ?? null);
  const [limit, setLimit] = useState('5000,00');
  const [miesiace, setMiesiace] = useState('36');
  const [bladPola, setBladPola] = useState<string>();
  const [wydana, setWydana] = useState<KartaWydana | null>(null);

  const zapis = useZapis((klucz, cialo: KartaNowa) => api.zapis<KartaWydana>('POST', '/karty', klucz, cialo));
  const bledy = bledyPol(zapis.blad);

  async function wyslij(e: FormEvent) {
    e.preventDefault();
    if (!klient) return setBladPola('Wybierz klienta');
    const grosze = naGrosze(limit);
    if (grosze === null) return setBladPola('Podaj limit, np. 5000,00');
    setBladPola(undefined);
    const wynik = await zapis.wykonaj({
      idKlienta: klient.id,
      limitKredytowy: grosze,
      waluta: 'PLN',
      miesiaceWaznosci: Number(miesiace),
    });
    if (wynik) {
      setWydana(wynik.dane);
      await queryClient.invalidateQueries({ queryKey: ['karty'] });
      await queryClient.invalidateQueries({ queryKey: ['klient-karty'] });
    }
  }

  if (wydana) {
    const k = wydana.karta;
    return (
      <Modal
        tytul="Karta wydana"
        onZamknij={onZamknij}
        tylkoPrzyciskiem={Boolean(wydana.numerKarty)}
        stopka={
          <button type="button" className="przycisk przycisk-glowny" onClick={onZamknij}>
            Zapisałem numer, zamknij
          </button>
        }
      >
        {wydana.numerKarty ? (
          <JednorazowaWartosc etykieta="Pełny numer karty" wartosc={grupujNumer(wydana.numerKarty)} />
        ) : (
          <div className="baner baner-info">
            To żądanie zostało już wykonane wcześniej, więc pełny numer nie jest już dostępny.
          </div>
        )}
        <Szczegoly
          wiersze={[
            ['ID karty', k.id],
            ['Maska', <code key="m">{k.numerKarty}</code>],
            ['Sieć (węzeł)', <Siec key="s" kod={k.siec} />],
            ['Klient', `#${k.idKlienta}`],
            ['Ważna do', k.dataWaznosci],
            ['Limit', kwota(k.limitKredytowy, k.waluta)],
            ['Wydana', data(k.dataWydania)],
          ]}
        />
      </Modal>
    );
  }

  return (
    <Modal
      tytul={`Wydanie karty ${nazwaSieci(siec)}`}
      onZamknij={onZamknij}
      stopka={
        <>
          <button type="button" className="przycisk" onClick={onZamknij}>
            Anuluj
          </button>
          <button type="submit" form="formularz-wydania" className="przycisk przycisk-glowny" disabled={zapis.trwa}>
            {zapis.trwa ? 'Wydawanie…' : 'Wydaj kartę'}
          </button>
        </>
      }
    >
      <form id="formularz-wydania" className="formularz" onSubmit={wyslij}>
        <p className="wyciszony maly">
          Karta powstanie na węźle sieci {nazwaSieci(siec)}. Bramka nada numer z BIN-u sieci i cyfrą kontrolną Luhna.
          Operacja wymaga dostępności węzła Visa (kopia główna klientów).
        </p>
        <BladApi blad={zapis.blad} />
        <Pole etykieta="Klient" blad={bledy.idKlienta}>
          <WyborKlienta wartosc={klient} onZmiana={setKlient} tylkoAktywni />
        </Pole>
        <div className="siatka-2">
          <Pole etykieta="Limit kredytowy (PLN)" blad={bladPola ?? bledy.limitKredytowy}>
            <input value={limit} onChange={(e) => setLimit(e.target.value)} inputMode="decimal" required />
          </Pole>
          <Pole etykieta="Ważność (miesiące)" blad={bledy.miesiaceWaznosci}>
            <input type="number" min={1} max={60} value={miesiace} onChange={(e) => setMiesiace(e.target.value)} />
          </Pole>
        </div>
      </form>
    </Modal>
  );
}
