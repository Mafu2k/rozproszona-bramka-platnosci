import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import type { Klient, KlientDane, KlientZmiana, Strona } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { usePowiadomienie } from '../components/Powiadomienia';
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
  Status,
  zapisWspolnychMozliwy,
} from '../components/ui';
import { bledyPol } from '../lib/bledy';
import { dzien } from '../lib/format';
import { useZapis } from '../lib/useZapis';

const ROZMIAR = 20;

export function Klienci() {
  const { czyAdmin } = useAuth();
  const [szukaj, setSzukaj] = useState('');
  const [filtr, setFiltr] = useState({ szukaj: '', status: '' });
  const [strona, setStrona] = useState(0);
  const [formularz, setFormularz] = useState<Klient | 'nowy' | null>(null);

  const q = useQuery({
    queryKey: ['klienci', filtr, strona],
    queryFn: () => api.get<Strona<Klient>>('/klienci', { ...filtr, strona, rozmiar: ROZMIAR }),
    placeholderData: keepPreviousData,
  });
  const dane = q.data?.dane;
  const zapisMozliwy = zapisWspolnychMozliwy(dane?.zrodlo);

  function filtruj(e: FormEvent) {
    e.preventDefault();
    setStrona(0);
    setFiltr((f) => ({ ...f, szukaj: szukaj.trim() }));
  }

  return (
    <>
      <NaglowekStrony
        tytul="Klienci"
        opis="Posiadacze kart. Zapis na węźle Visa (kopia główna), odczyt z każdego węzła."
        akcje={
          czyAdmin && (
            <button
              type="button"
              className="przycisk przycisk-glowny"
              onClick={() => setFormularz('nowy')}
              disabled={!zapisMozliwy}
              title={zapisMozliwy ? undefined : 'Węzeł Visa niedostępny'}
            >
              + Dodaj klienta
            </button>
          )
        }
      />

      <form className="filtry" onSubmit={filtruj}>
        <input
          type="search"
          placeholder="Imię, nazwisko albo e-mail"
          value={szukaj}
          onChange={(e) => setSzukaj(e.target.value)}
        />
        <select
          value={filtr.status}
          onChange={(e) => {
            setStrona(0);
            setFiltr((f) => ({ ...f, status: e.target.value }));
          }}
        >
          <option value="">Wszystkie statusy</option>
          <option value="AKTYWNY">Aktywni</option>
          <option value="ZABLOKOWANY">Zablokowani</option>
          <option value="USUNIETY">Usunięci</option>
        </select>
        <button type="submit" className="przycisk">
          Szukaj
        </button>
        <CzasOdpowiedzi ms={q.data?.ms} laduje={q.isFetching} />
      </form>

      <BanerDanych dane={dane} />
      <BladApi blad={q.error} />

      {q.isLoading ? (
        <Ladowanie />
      ) : dane && dane.elementy.length === 0 ? (
        <Pusto>Brak klientów spełniających kryteria.</Pusto>
      ) : (
        dane && (
          <div className="tabela-kontener">
            <table className="tabela">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Imię i nazwisko</th>
                  <th>E-mail</th>
                  <th>Telefon</th>
                  <th>Kraj</th>
                  <th>Rejestracja</th>
                  <th>Status</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {dane.elementy.map((k) => (
                  <tr key={k.id}>
                    <td className="liczba">{k.id}</td>
                    <td>
                      <Link to={`/klienci/${k.id}`}>
                        {k.imie} {k.nazwisko}
                      </Link>
                    </td>
                    <td>{k.email}</td>
                    <td>{k.telefon ?? '—'}</td>
                    <td>{k.kraj}</td>
                    <td>{dzien(k.dataRejestracji)}</td>
                    <td>
                      <Status wartosc={k.status} />
                    </td>
                    <td className="akcje-wiersza">
                      <Link to={`/klienci/${k.id}`} className="przycisk przycisk-maly">
                        Szczegóły
                      </Link>
                      {czyAdmin && k.status !== 'USUNIETY' && (
                        <button
                          type="button"
                          className="przycisk przycisk-maly"
                          disabled={!zapisMozliwy}
                          onClick={() => setFormularz(k)}
                        >
                          Edytuj
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )
      )}
      {dane && <Paginacja strona={strona} rozmiar={ROZMIAR} lacznie={dane.lacznie} onStrona={setStrona} />}

      {formularz && (
        <FormularzKlienta klient={formularz === 'nowy' ? null : formularz} onZamknij={() => setFormularz(null)} />
      )}
    </>
  );
}

/** Dodanie (POST /klienci) albo edycja (PUT /klienci/{id}) klienta. */
export function FormularzKlienta({ klient, onZamknij }: { klient: Klient | null; onZamknij: () => void }) {
  const queryClient = useQueryClient();
  const pokaz = usePowiadomienie();
  const [f, setF] = useState({
    imie: klient?.imie ?? '',
    nazwisko: klient?.nazwisko ?? '',
    email: klient?.email ?? '',
    telefon: klient?.telefon ?? '',
    kraj: klient?.kraj ?? 'PL',
    status: (klient?.status === 'ZABLOKOWANY' ? 'ZABLOKOWANY' : 'AKTYWNY') as KlientZmiana['status'],
  });

  const zapis = useZapis(async (klucz, cialo: KlientDane | KlientZmiana) =>
    klient
      ? api.zapis<Klient>('PUT', `/klienci/${klient.id}`, klucz, cialo)
      : api.zapis<Klient>('POST', '/klienci', klucz, cialo),
  );
  const bledy = bledyPol(zapis.blad);

  async function wyslij(e: FormEvent) {
    e.preventDefault();
    const dane: KlientDane = {
      imie: f.imie.trim(),
      nazwisko: f.nazwisko.trim(),
      email: f.email.trim(),
      telefon: f.telefon.trim() || null,
      kraj: f.kraj.trim().toUpperCase(),
    };
    const wynik = await zapis.wykonaj(klient ? { ...dane, status: f.status } : dane);
    if (wynik) {
      await queryClient.invalidateQueries({ queryKey: ['klienci'] });
      pokaz(
        klient
          ? `Zapisano zmiany klienta #${wynik.dane.id}`
          : `Dodano klienta #${wynik.dane.id}. Repliki MC i AmEx zobaczą go w ciągu ok. 30 s.`,
      );
      onZamknij();
    }
  }

  const ustaw = (k: keyof typeof f) => (e: { target: { value: string } }) => setF({ ...f, [k]: e.target.value });

  return (
    <Modal
      tytul={klient ? `Edycja klienta #${klient.id}` : 'Nowy klient'}
      onZamknij={onZamknij}
      stopka={
        <>
          <button type="button" className="przycisk" onClick={onZamknij}>
            Anuluj
          </button>
          <button type="submit" form="formularz-klienta" className="przycisk przycisk-glowny" disabled={zapis.trwa}>
            {zapis.trwa ? 'Zapisywanie…' : 'Zapisz'}
          </button>
        </>
      }
    >
      <form id="formularz-klienta" className="formularz" onSubmit={wyslij}>
        <BladApi blad={zapis.blad} />
        <div className="siatka-2">
          <Pole etykieta="Imię" blad={bledy.imie}>
            <input value={f.imie} onChange={ustaw('imie')} required maxLength={50} />
          </Pole>
          <Pole etykieta="Nazwisko" blad={bledy.nazwisko}>
            <input value={f.nazwisko} onChange={ustaw('nazwisko')} required maxLength={80} />
          </Pole>
        </div>
        <Pole etykieta="E-mail" blad={bledy.email}>
          <input type="email" value={f.email} onChange={ustaw('email')} required maxLength={120} />
        </Pole>
        <div className="siatka-2">
          <Pole etykieta="Telefon" blad={bledy.telefon} podpowiedz="Opcjonalnie">
            <input value={f.telefon} onChange={ustaw('telefon')} placeholder="+48600700800" pattern="\+?[0-9 ]{7,20}" />
          </Pole>
          <Pole etykieta="Kraj (ISO)" blad={bledy.kraj} podpowiedz="Dwie litery, np. PL">
            <input value={f.kraj} onChange={ustaw('kraj')} required pattern="[A-Za-z]{2}" maxLength={2} />
          </Pole>
        </div>
        {klient && (
          <Pole etykieta="Status" blad={bledy.status}>
            <select value={f.status} onChange={ustaw('status')}>
              <option value="AKTYWNY">AKTYWNY</option>
              <option value="ZABLOKOWANY">ZABLOKOWANY</option>
            </select>
          </Pole>
        )}
      </form>
    </Modal>
  );
}
