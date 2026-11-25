import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import type { NowyKluczApi, Sklep, SklepDane, SklepZmiana, Strona } from '../api/types';
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
  Status,
  zapisWspolnychMozliwy,
} from '../components/ui';
import { bledyPol } from '../lib/bledy';
import { zapiszKluczSymulatora } from '../lib/symulator';
import { useZapis } from '../lib/useZapis';

const ROZMIAR = 20;

type Okno = { typ: 'formularz'; sklep: Sklep | null } | { typ: 'klucz'; sklep: Sklep } | { typ: 'usun'; sklep: Sklep };

export function Sklepy() {
  const { czyAdmin } = useAuth();
  const [szukaj, setSzukaj] = useState('');
  const [filtr, setFiltr] = useState({ szukaj: '', status: '' });
  const [strona, setStrona] = useState(0);
  const [okno, setOkno] = useState<Okno | null>(null);

  const q = useQuery({
    queryKey: ['sklepy', filtr, strona],
    queryFn: () => api.get<Strona<Sklep>>('/sklepy', { ...filtr, strona, rozmiar: ROZMIAR }),
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
        tytul="Sklepy"
        opis="Sklepy internetowe korzystające z bramki. Zapis na węźle Visa, repliki na pozostałych węzłach."
        akcje={
          czyAdmin && (
            <button
              type="button"
              className="przycisk przycisk-glowny"
              disabled={!zapisMozliwy}
              onClick={() => setOkno({ typ: 'formularz', sklep: null })}
            >
              + Dodaj sklep
            </button>
          )
        }
      />

      <form className="filtry" onSubmit={filtruj}>
        <input type="search" placeholder="Nazwa albo NIP" value={szukaj} onChange={(e) => setSzukaj(e.target.value)} />
        <select
          value={filtr.status}
          onChange={(e) => {
            setStrona(0);
            setFiltr((f) => ({ ...f, status: e.target.value }));
          }}
        >
          <option value="">Wszystkie statusy</option>
          <option value="AKTYWNY">Aktywne</option>
          <option value="ZAWIESZONY">Zawieszone</option>
          <option value="USUNIETY">Usunięte</option>
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
        <Pusto>Brak sklepów spełniających kryteria.</Pusto>
      ) : (
        dane && (
          <div className="tabela-kontener">
            <table className="tabela">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Nazwa</th>
                  <th>NIP</th>
                  <th>MCC</th>
                  <th>Kontakt</th>
                  <th>Klucz API</th>
                  <th>Status</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {dane.elementy.map((s) => (
                  <tr key={s.id}>
                    <td className="liczba">{s.id}</td>
                    <td>
                      <strong>{s.nazwa}</strong>
                      {s.url && (
                        <div className="maly">
                          <a href={s.url} target="_blank" rel="noreferrer">
                            {s.url}
                          </a>
                        </div>
                      )}
                    </td>
                    <td>
                      <code>{s.nip}</code>
                    </td>
                    <td>{s.kategoriaMcc}</td>
                    <td>{s.emailKontaktowy}</td>
                    <td>{s.maKluczApi ? 'tak' : <span className="wyciszony">brak</span>}</td>
                    <td>
                      <Status wartosc={s.status} />
                    </td>
                    <td className="akcje-wiersza">
                      <Link to={`/transakcje?idSklepu=${s.id}`} className="przycisk przycisk-maly">
                        Transakcje
                      </Link>
                      {czyAdmin && (
                        <Link to={`/raporty/obroty-sklepu?idSklepu=${s.id}`} className="przycisk przycisk-maly">
                          Raport
                        </Link>
                      )}
                      {czyAdmin && s.status !== 'USUNIETY' && (
                        <>
                          <button
                            type="button"
                            className="przycisk przycisk-maly"
                            disabled={!zapisMozliwy}
                            onClick={() => setOkno({ typ: 'formularz', sklep: s })}
                          >
                            Edytuj
                          </button>
                          <button
                            type="button"
                            className="przycisk przycisk-maly"
                            disabled={!zapisMozliwy}
                            onClick={() => setOkno({ typ: 'klucz', sklep: s })}
                          >
                            Klucz API
                          </button>
                          <button
                            type="button"
                            className="przycisk przycisk-maly przycisk-niebezpieczny-tekst"
                            disabled={!zapisMozliwy}
                            onClick={() => setOkno({ typ: 'usun', sklep: s })}
                          >
                            Usuń
                          </button>
                        </>
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

      {okno?.typ === 'formularz' && <FormularzSklepu sklep={okno.sklep} onZamknij={() => setOkno(null)} />}
      {okno?.typ === 'klucz' && <KluczApi sklep={okno.sklep} onZamknij={() => setOkno(null)} />}
      {okno?.typ === 'usun' && <UsunSklep sklep={okno.sklep} onZamknij={() => setOkno(null)} />}
    </>
  );
}

function FormularzSklepu({ sklep, onZamknij }: { sklep: Sklep | null; onZamknij: () => void }) {
  const queryClient = useQueryClient();
  const pokaz = usePowiadomienie();
  const [f, setF] = useState({
    nazwa: sklep?.nazwa ?? '',
    nip: sklep?.nip ?? '',
    kategoriaMcc: sklep?.kategoriaMcc ?? '',
    url: sklep?.url ?? '',
    emailKontaktowy: sklep?.emailKontaktowy ?? '',
    status: (sklep?.status === 'ZAWIESZONY' ? 'ZAWIESZONY' : 'AKTYWNY') as SklepZmiana['status'],
  });
  const zapis = useZapis(async (klucz, cialo: SklepDane | SklepZmiana) =>
    sklep
      ? api.zapis<Sklep>('PUT', `/sklepy/${sklep.id}`, klucz, cialo)
      : api.zapis<Sklep>('POST', '/sklepy', klucz, cialo),
  );
  const bledy = bledyPol(zapis.blad);
  const ustaw = (k: keyof typeof f) => (e: { target: { value: string } }) => setF({ ...f, [k]: e.target.value });

  async function wyslij(e: FormEvent) {
    e.preventDefault();
    const dane: SklepDane = {
      nazwa: f.nazwa.trim(),
      nip: f.nip.replace(/[\s-]/g, ''),
      kategoriaMcc: f.kategoriaMcc.trim(),
      url: f.url.trim() || null,
      emailKontaktowy: f.emailKontaktowy.trim(),
    };
    const wynik = await zapis.wykonaj(sklep ? { ...dane, status: f.status } : dane);
    if (wynik) {
      await queryClient.invalidateQueries({ queryKey: ['sklepy'] });
      pokaz(sklep ? `Zapisano zmiany sklepu #${wynik.dane.id}` : `Dodano sklep #${wynik.dane.id}`);
      onZamknij();
    }
  }

  return (
    <Modal
      tytul={sklep ? `Edycja sklepu #${sklep.id}` : 'Nowy sklep'}
      onZamknij={onZamknij}
      stopka={
        <>
          <button type="button" className="przycisk" onClick={onZamknij}>
            Anuluj
          </button>
          <button type="submit" form="formularz-sklepu" className="przycisk przycisk-glowny" disabled={zapis.trwa}>
            {zapis.trwa ? 'Zapisywanie…' : 'Zapisz'}
          </button>
        </>
      }
    >
      <form id="formularz-sklepu" className="formularz" onSubmit={wyslij}>
        <BladApi blad={zapis.blad} />
        <Pole etykieta="Nazwa" blad={bledy.nazwa}>
          <input value={f.nazwa} onChange={ustaw('nazwa')} required maxLength={100} />
        </Pole>
        <div className="siatka-2">
          <Pole etykieta="NIP" blad={bledy.nip} podpowiedz="10 cyfr z poprawną cyfrą kontrolną">
            <input value={f.nip} onChange={ustaw('nip')} required inputMode="numeric" />
          </Pole>
          <Pole etykieta="Kategoria MCC" blad={bledy.kategoriaMcc} podpowiedz="4 cyfry, np. 5732 (elektronika)">
            <input value={f.kategoriaMcc} onChange={ustaw('kategoriaMcc')} required pattern="[0-9]{4}" maxLength={4} />
          </Pole>
        </div>
        <Pole etykieta="Adres strony" blad={bledy.url} podpowiedz="Opcjonalnie">
          <input type="url" value={f.url} onChange={ustaw('url')} placeholder="https://sklep.example.com" />
        </Pole>
        <Pole etykieta="E-mail kontaktowy" blad={bledy.emailKontaktowy}>
          <input type="email" value={f.emailKontaktowy} onChange={ustaw('emailKontaktowy')} required maxLength={120} />
        </Pole>
        {sklep && (
          <Pole etykieta="Status" blad={bledy.status}>
            <select value={f.status} onChange={ustaw('status')}>
              <option value="AKTYWNY">AKTYWNY</option>
              <option value="ZAWIESZONY">ZAWIESZONY</option>
            </select>
          </Pole>
        )}
      </form>
    </Modal>
  );
}

function KluczApi({ sklep, onZamknij }: { sklep: Sklep; onZamknij: () => void }) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [klucz, setKlucz] = useState<NowyKluczApi | null>(null);
  const zapis = useZapis((k) => api.zapis<NowyKluczApi>('POST', `/sklepy/${sklep.id}/klucz-api`, k));

  async function generuj() {
    const wynik = await zapis.wykonaj(undefined);
    if (wynik) {
      setKlucz(wynik.dane);
      await queryClient.invalidateQueries({ queryKey: ['sklepy'] });
    }
  }

  if (klucz) {
    return (
      <Modal
        tytul={`Klucz API: ${sklep.nazwa}`}
        onZamknij={onZamknij}
        tylkoPrzyciskiem={Boolean(klucz.kluczApi)}
        stopka={
          <>
            {klucz.kluczApi && (
              <button
                type="button"
                className="przycisk"
                onClick={() => {
                  zapiszKluczSymulatora({ kluczApi: klucz.kluczApi!, idSklepu: sklep.id, nazwaSklepu: sklep.nazwa });
                  navigate('/symulator');
                }}
              >
                Użyj w symulatorze sklepu
              </button>
            )}
            <button type="button" className="przycisk przycisk-glowny" onClick={onZamknij}>
              Zapisałem klucz, zamknij
            </button>
          </>
        }
      >
        {klucz.kluczApi ? (
          <JednorazowaWartosc etykieta="Nowy klucz API (nagłówek X-Api-Key)" wartosc={klucz.kluczApi} />
        ) : (
          <div className="baner baner-info">To żądanie zostało już wykonane, klucz nie jest ponownie pokazywany.</div>
        )}
      </Modal>
    );
  }

  return (
    <Potwierdzenie
      tytul="Nowy klucz API"
      tresc={
        <p>
          Wygenerować nowy klucz API dla sklepu <strong>{sklep.nazwa}</strong>?
          {sklep.maKluczApi && ' Poprzedni klucz przestanie działać.'}
        </p>
      }
      przycisk="Generuj klucz"
      trwa={zapis.trwa}
      blad={zapis.blad}
      onTak={() => void generuj()}
      onNie={onZamknij}
    />
  );
}

function UsunSklep({ sklep, onZamknij }: { sklep: Sklep; onZamknij: () => void }) {
  const queryClient = useQueryClient();
  const pokaz = usePowiadomienie();
  const zapis = useZapis((k) => api.zapis<void>('DELETE', `/sklepy/${sklep.id}`, k));

  async function usun() {
    const wynik = await zapis.wykonaj(undefined);
    if (wynik) {
      await queryClient.invalidateQueries({ queryKey: ['sklepy'] });
      pokaz(`Sklep #${sklep.id} usunięty`);
      onZamknij();
    }
  }

  return (
    <Potwierdzenie
      tytul="Usunięcie sklepu"
      tresc={
        <p>
          Usunąć sklep <strong>{sklep.nazwa}</strong>? Usunięcie jest logiczne (status USUNIETY), a klucz API zostanie
          unieważniony.
        </p>
      }
      przycisk="Usuń sklep"
      niebezpieczne
      trwa={zapis.trwa}
      blad={zapis.blad}
      onTak={() => void usun()}
      onNie={onZamknij}
    />
  );
}
