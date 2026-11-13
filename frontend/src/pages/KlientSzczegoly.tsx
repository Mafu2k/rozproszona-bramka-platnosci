import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import type { Klient, ListaKart, WynikBlokadyGlobalnej } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { usePowiadomienie } from '../components/Powiadomienia';
import {
  BanerDanych,
  BladApi,
  CzasOdpowiedzi,
  Ladowanie,
  Modal,
  NaglowekStrony,
  Potwierdzenie,
  Pusto,
  Siec,
  Status,
  Szczegoly,
} from '../components/ui';
import { data, nazwaSieci } from '../lib/format';
import { useZapis } from '../lib/useZapis';
import { FormularzKlienta } from './Klienci';
import { FormularzWydania, TabelaKart } from './Karty';

export function KlientSzczegoly() {
  const id = Number(useParams().id);
  const { czyAdmin, czyOperator } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const pokaz = usePowiadomienie();
  const [okno, setOkno] = useState<'edycja' | 'usun' | 'blokada' | 'karta' | null>(null);
  const [wynikBlokady, setWynikBlokady] = useState<WynikBlokadyGlobalnej | null>(null);

  const qKlient = useQuery({ queryKey: ['klienci', 'szczegoly', id], queryFn: () => api.get<Klient>(`/klienci/${id}`) });
  // Odczyt z wielu węzłów: karty klienta z Visa, MasterCard i AmEx łączone w jedną listę.
  const qKarty = useQuery({ queryKey: ['klient-karty', id], queryFn: () => api.get<ListaKart>(`/klienci/${id}/karty`) });

  const usuniecie = useZapis((klucz) => api.zapis<void>('DELETE', `/klienci/${id}`, klucz));
  const blokada = useZapis((klucz) =>
    api.zapis<WynikBlokadyGlobalnej>('POST', `/klienci/${id}/blokada-globalna`, klucz),
  );

  const klient = qKlient.data?.dane;
  const karty = qKarty.data?.dane;

  async function usun() {
    const wynik = await usuniecie.wykonaj(undefined);
    if (wynik) {
      await queryClient.invalidateQueries({ queryKey: ['klienci'] });
      pokaz(`Klient #${id} usunięty (status USUNIETY)`);
      navigate('/klienci');
    }
  }

  async function zablokuj() {
    const wynik = await blokada.wykonaj(undefined);
    if (wynik) {
      setOkno(null);
      setWynikBlokady(wynik.dane);
      await queryClient.invalidateQueries({ queryKey: ['klienci'] });
      await queryClient.invalidateQueries({ queryKey: ['klient-karty', id] });
      await queryClient.invalidateQueries({ queryKey: ['karty'] });
    }
  }

  if (qKlient.isLoading) return <Ladowanie />;
  if (!klient) return <BladApi blad={qKlient.error} />;

  const aktywneKarty = karty?.elementy.filter((k) => k.status === 'AKTYWNA').length ?? 0;
  const sieciKlienta = [...new Set(karty?.elementy.map((k) => k.siec) ?? [])];

  return (
    <>
      <div className="okruszki">
        <Link to="/klienci">Klienci</Link> / #{klient.id}
      </div>
      <NaglowekStrony
        tytul={`${klient.imie} ${klient.nazwisko}`}
        opis={<Status wartosc={klient.status} />}
        akcje={
          <>
            {czyOperator && klient.status === 'AKTYWNY' && (
              <button type="button" className="przycisk przycisk-glowny" onClick={() => setOkno('karta')}>
                + Wydaj kartę
              </button>
            )}
            {czyAdmin && klient.status !== 'USUNIETY' && (
              <>
                <button type="button" className="przycisk" onClick={() => setOkno('edycja')}>
                  Edytuj
                </button>
                <button
                  type="button"
                  className="przycisk przycisk-niebezpieczny"
                  onClick={() => {
                    blokada.wyczyscBlad();
                    setOkno('blokada');
                  }}
                >
                  Zablokuj we wszystkich sieciach
                </button>
                <button
                  type="button"
                  className="przycisk przycisk-niebezpieczny-tekst"
                  onClick={() => {
                    usuniecie.wyczyscBlad();
                    setOkno('usun');
                  }}
                >
                  Usuń
                </button>
              </>
            )}
          </>
        }
      />

      <section className="panel">
        <h2>Dane klienta</h2>
        <Szczegoly
          wiersze={[
            ['ID', klient.id],
            ['E-mail', klient.email],
            ['Telefon', klient.telefon ?? '—'],
            ['Kraj', klient.kraj],
            ['Data rejestracji', data(klient.dataRejestracji)],
            ['Status', <Status key="s" wartosc={klient.status} />],
          ]}
        />
      </section>

      <section className="panel">
        <div className="panel-naglowek">
          <h2>Karty ze wszystkich sieci</h2>
          <CzasOdpowiedzi ms={qKarty.data?.ms} laduje={qKarty.isFetching} />
        </div>
        <p className="wyciszony maly">
          Zapytanie rozproszone: bramka odpytuje węzły Visa, MasterCard i American Express i łączy wyniki.
          {czyOperator && ' Jako operator widzisz tylko karty swojej sieci.'}
        </p>
        <BanerDanych dane={karty} />
        <BladApi blad={qKarty.error} />
        {qKarty.isLoading ? (
          <Ladowanie />
        ) : karty && karty.elementy.length === 0 ? (
          <Pusto>Klient nie ma kart.</Pusto>
        ) : (
          karty && <TabelaKart karty={karty.elementy} bezKlienta />
        )}
      </section>

      {okno === 'edycja' && <FormularzKlienta klient={klient} onZamknij={() => setOkno(null)} />}
      {okno === 'karta' && <FormularzWydania klient={klient} onZamknij={() => setOkno(null)} />}

      {okno === 'usun' && (
        <Potwierdzenie
          tytul="Usunięcie klienta"
          tresc={
            <>
              <p>
                Usunąć klienta <strong>{`${klient.imie} ${klient.nazwisko}`}</strong>? Usunięcie jest logiczne (status
                USUNIETY).
              </p>
              <p className="wyciszony maly">
                Backend sprawdza na wszystkich węzłach, czy klient nie ma aktywnych kart. Wymaga dostępności wszystkich
                węzłów.
                {aktywneKarty > 0 && ` Klient ma obecnie ${aktywneKarty} aktywnych kart, więc usunięcie zostanie odrzucone.`}
              </p>
            </>
          }
          przycisk="Usuń klienta"
          niebezpieczne
          trwa={usuniecie.trwa}
          blad={usuniecie.blad}
          onTak={() => void usun()}
          onNie={() => setOkno(null)}
        />
      )}

      {okno === 'blokada' && (
        <Potwierdzenie
          tytul="Blokada we wszystkich sieciach (FR7)"
          tresc={
            <>
              <p>
                Zablokować wszystkie karty klienta <strong>{`${klient.imie} ${klient.nazwisko}`}</strong> we wszystkich
                sieciach?
              </p>
              <p className="wyciszony maly">
                To jedna transakcja rozproszona zatwierdzana protokołem dwufazowym (2PC). Jeśli którykolwiek węzeł jest
                niedostępny, zmiana zostanie wycofana na wszystkich węzłach i żadna karta nie zostanie zablokowana.
                {sieciKlienta.length > 0 && ` Klient ma karty w sieciach: ${sieciKlienta.map(nazwaSieci).join(', ')}.`}
              </p>
            </>
          }
          przycisk="Zablokuj wszędzie"
          niebezpieczne
          trwa={blokada.trwa}
          blad={blokada.blad}
          onTak={() => void zablokuj()}
          onNie={() => setOkno(null)}
        />
      )}

      {wynikBlokady && (
        <Modal
          tytul="Klient zablokowany we wszystkich sieciach"
          onZamknij={() => setWynikBlokady(null)}
          stopka={
            <button type="button" className="przycisk przycisk-glowny" onClick={() => setWynikBlokady(null)}>
              OK
            </button>
          }
        >
          <p>Transakcja 2PC zatwierdzona na wszystkich węzłach. Zablokowane karty:</p>
          <table className="tabela">
            <thead>
              <tr>
                <th>Sieć</th>
                <th className="liczba">Zablokowane karty</th>
              </tr>
            </thead>
            <tbody>
              {Object.entries(wynikBlokady.zablokowaneKarty).map(([s, n]) => (
                <tr key={s}>
                  <td>
                    <Siec kod={s} />
                  </td>
                  <td className="liczba">{n}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Modal>
      )}
    </>
  );
}
