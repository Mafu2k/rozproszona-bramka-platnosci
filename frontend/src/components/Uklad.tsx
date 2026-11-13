import { useEffect, useRef, useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  ChartBar,
  ChartPieSlice,
  CreditCard,
  HardDrives,
  List,
  Receipt,
  ShoppingCart,
  SignOut,
  Storefront,
  Users,
  X,
  type Icon,
} from '@phosphor-icons/react';
import { useAuth } from '../auth/AuthContext';
import { api } from '../api/client';
import type { StanWezlow } from '../api/types';
import { nazwaSieci } from '../lib/format';

interface Pozycja {
  do: string;
  etykieta: string;
  ikona: Icon;
  fr?: string;
  tylkoAdmin?: boolean;
}

const MENU: { grupa: string; pozycje: Pozycja[] }[] = [
  {
    grupa: 'Dane',
    pozycje: [
      { do: '/klienci', etykieta: 'Klienci', ikona: Users, fr: 'FR4' },
      { do: '/sklepy', etykieta: 'Sklepy', ikona: Storefront, fr: 'FR5' },
      { do: '/karty', etykieta: 'Karty', ikona: CreditCard, fr: 'FR6' },
      { do: '/transakcje', etykieta: 'Transakcje', ikona: Receipt, fr: 'FR8' },
    ],
  },
  {
    grupa: 'Raporty',
    pozycje: [
      { do: '/raporty/obroty-sklepu', etykieta: 'Obroty sklepu', ikona: ChartBar, fr: 'FR9', tylkoAdmin: true },
      { do: '/raporty/statystyki-sieci', etykieta: 'Statystyki sieci', ikona: ChartPieSlice, fr: 'FR10', tylkoAdmin: true },
    ],
  },
  {
    grupa: 'System',
    pozycje: [
      { do: '/wezly', etykieta: 'Stan węzłów', ikona: HardDrives, fr: 'FR11', tylkoAdmin: true },
      { do: '/symulator', etykieta: 'Symulator sklepu', ikona: ShoppingCart, fr: 'FR2/3' },
    ],
  },
];

export function Logo({ podpis }: { podpis: string }) {
  return (
    <div className="logo">
      <span className="logo-znak" aria-hidden>
        <CreditCard weight="fill" size={20} />
      </span>
      <div>
        <strong>Bramka płatności</strong>
        <div className="maly wyciszony">{podpis}</div>
      </div>
    </div>
  );
}

/** Wskaźnik dostępności węzłów w nagłówku (tylko administrator ma dostęp do /wezly/stan). */
function WskaznikWezlow() {
  const q = useQuery({
    queryKey: ['wezly'],
    queryFn: () => api.get<StanWezlow>('/wezly/stan'),
    refetchInterval: 5000,
  });
  const wezly = q.data?.dane.wezly ?? [];
  if (q.isError) return <span className="wskaznik wyciszony">stan węzłów: brak danych</span>;
  return (
    <NavLink to="/wezly" className="wskaznik" aria-label="Stan węzłów, odświeżany co 5 sekund">
      {wezly.map((w) => (
        <span key={w.siec} className={`kropka ${w.dostepny ? 'kropka-ok' : 'kropka-blad'}`}>
          {nazwaSieci(w.siec)}
          <span className="sr-only">{w.dostepny ? ' dostępny' : ' niedostępny'}</span>
        </span>
      ))}
    </NavLink>
  );
}

export function Uklad() {
  const { uzytkownik, czyAdmin, siec, wyloguj } = useAuth();
  const [menuOtwarte, setMenuOtwarte] = useState(false);
  const location = useLocation();
  const tresc = useRef<HTMLElement>(null);
  const pierwszy = useRef(true);

  // Po zmianie strony zamykamy menu mobilne i przenosimy fokus na treść (czytniki ekranu).
  useEffect(() => {
    setMenuOtwarte(false);
    if (pierwszy.current) {
      pierwszy.current = false;
      return;
    }
    tresc.current?.focus({ preventScroll: true });
  }, [location.pathname]);

  return (
    <div className={`uklad${menuOtwarte ? ' menu-otwarte' : ''}`}>
      <a href="#tresc" className="pomin">
        Przejdź do treści
      </a>
      <aside className="boczny" id="menu-boczne">
        <Logo podpis="panel administracyjny" />
        <nav aria-label="Menu główne">
          {MENU.map((g) => {
            const pozycje = g.pozycje.filter((p) => !p.tylkoAdmin || czyAdmin);
            if (pozycje.length === 0) return null;
            return (
              <div key={g.grupa} className="menu-grupa">
                <div className="menu-tytul">{g.grupa}</div>
                {pozycje.map((p) => (
                  <NavLink key={p.do} to={p.do} className={({ isActive }) => `menu-link${isActive ? ' aktywny' : ''}`}>
                    <p.ikona aria-hidden size={18} />
                    <span>{p.etykieta}</span>
                    {p.fr && <span className="menu-fr">{p.fr}</span>}
                  </NavLink>
                ))}
              </div>
            );
          })}
        </nav>
        <div className="boczny-stopka">
          <div className="uzytkownik">
            <strong>{uzytkownik?.login}</strong>
            <span className="maly wyciszony">
              {czyAdmin ? 'Administrator bramki' : `Operator sieci ${nazwaSieci(siec)}`}
            </span>
          </div>
          <button type="button" className="przycisk przycisk-pelny" onClick={() => void wyloguj()}>
            <SignOut aria-hidden /> Wyloguj
          </button>
        </div>
      </aside>
      <div className="zaslona" onClick={() => setMenuOtwarte(false)} aria-hidden />

      <div className="glowna">
        <header className="gora">
          <button
            type="button"
            className="przycisk-ikona przycisk-menu"
            aria-label={menuOtwarte ? 'Zamknij menu' : 'Otwórz menu'}
            aria-expanded={menuOtwarte}
            aria-controls="menu-boczne"
            onClick={() => setMenuOtwarte((o) => !o)}
          >
            {menuOtwarte ? <X aria-hidden size={22} /> : <List aria-hidden size={22} />}
          </button>
          {czyAdmin ? <WskaznikWezlow /> : <span className="wyciszony maly">Sieć: {nazwaSieci(siec)}</span>}
        </header>
        <main className="tresc" id="tresc" ref={tresc} tabIndex={-1}>
          <Outlet />
        </main>
      </div>
    </div>
  );
}
