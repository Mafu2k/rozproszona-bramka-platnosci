import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import type { Klient, Sklep, Strona } from '../api/types';

function useOpoznienie<T>(wartosc: T, ms = 300): T {
  const [v, setV] = useState(wartosc);
  useEffect(() => {
    const t = window.setTimeout(() => setV(wartosc), ms);
    return () => window.clearTimeout(t);
  }, [wartosc, ms]);
  return v;
}

/** Pole wyboru klienta z wyszukiwaniem po imieniu, nazwisku albo e-mailu. */
export function WyborKlienta({
  wartosc,
  onZmiana,
  tylkoAktywni,
}: {
  wartosc: Klient | null;
  onZmiana: (k: Klient | null) => void;
  tylkoAktywni?: boolean;
}) {
  const [tekst, setTekst] = useState('');
  const szukaj = useOpoznienie(tekst);
  const q = useQuery({
    queryKey: ['klienci', 'wybor', szukaj, tylkoAktywni],
    queryFn: () =>
      api.get<Strona<Klient>>('/klienci', { szukaj, rozmiar: 8, status: tylkoAktywni ? 'AKTYWNY' : undefined }),
    enabled: !wartosc,
  });

  if (wartosc) {
    return (
      <div className="wybrane">
        <span>
          <strong>
            {wartosc.imie} {wartosc.nazwisko}
          </strong>{' '}
          <span className="wyciszony">
            #{wartosc.id} · {wartosc.email}
          </span>
        </span>
        <button type="button" className="przycisk przycisk-maly" onClick={() => onZmiana(null)}>
          Zmień
        </button>
      </div>
    );
  }

  const elementy = q.data?.dane.elementy ?? [];
  return (
    <div className="wyszukiwarka">
      <input
        type="search"
        value={tekst}
        onChange={(e) => setTekst(e.target.value)}
        placeholder="Szukaj: imię, nazwisko, e-mail…"
      />
      <ul className="wyszukiwarka-lista">
        {q.isLoading && <li className="wyciszony">Szukam…</li>}
        {q.isError && <li className="tekst-blad">Nie udało się pobrać klientów</li>}
        {!q.isLoading && elementy.length === 0 && <li className="wyciszony">Brak wyników</li>}
        {elementy.map((k) => (
          <li key={k.id}>
            <button type="button" onClick={() => onZmiana(k)}>
              <strong>
                {k.imie} {k.nazwisko}
              </strong>
              <span className="wyciszony">
                #{k.id} · {k.email} · {k.status}
              </span>
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}

/** Pole wyboru sklepu z wyszukiwaniem po nazwie albo NIP. */
export function WyborSklepu({ wartosc, onZmiana }: { wartosc: Sklep | null; onZmiana: (s: Sklep | null) => void }) {
  const [tekst, setTekst] = useState('');
  const szukaj = useOpoznienie(tekst);
  const q = useQuery({
    queryKey: ['sklepy', 'wybor', szukaj],
    queryFn: () => api.get<Strona<Sklep>>('/sklepy', { szukaj, rozmiar: 8 }),
    enabled: !wartosc,
  });

  if (wartosc) {
    return (
      <div className="wybrane">
        <span>
          <strong>{wartosc.nazwa}</strong>{' '}
          <span className="wyciszony">
            #{wartosc.id} · NIP {wartosc.nip}
          </span>
        </span>
        <button type="button" className="przycisk przycisk-maly" onClick={() => onZmiana(null)}>
          Zmień
        </button>
      </div>
    );
  }

  const elementy = q.data?.dane.elementy ?? [];
  return (
    <div className="wyszukiwarka">
      <input type="search" value={tekst} onChange={(e) => setTekst(e.target.value)} placeholder="Szukaj: nazwa albo NIP…" />
      <ul className="wyszukiwarka-lista">
        {q.isLoading && <li className="wyciszony">Szukam…</li>}
        {q.isError && <li className="tekst-blad">Nie udało się pobrać sklepów</li>}
        {!q.isLoading && elementy.length === 0 && <li className="wyciszony">Brak wyników</li>}
        {elementy.map((s) => (
          <li key={s.id}>
            <button type="button" onClick={() => onZmiana(s)}>
              <strong>{s.nazwa}</strong>
              <span className="wyciszony">
                #{s.id} · NIP {s.nip} · {s.status}
              </span>
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
