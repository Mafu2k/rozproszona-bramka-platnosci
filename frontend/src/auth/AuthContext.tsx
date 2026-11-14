import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { api, przyBrakuUwierzytelnienia, ustawToken } from '../api/client';
import type { KodSieci, TokenLogowania, Uzytkownik } from '../api/types';
import { zapiszKluczSymulatora } from '../lib/symulator';

interface Sesja {
  token: string;
  wygasa: string;
  uzytkownik: Uzytkownik;
}

interface KontekstAuth {
  uzytkownik: Uzytkownik | null;
  czyAdmin: boolean;
  czyOperator: boolean;
  /** Sieć operatora; null dla administratora. */
  siec: KodSieci | null;
  zaloguj: (login: string, haslo: string) => Promise<void>;
  wyloguj: () => Promise<void>;
  komunikatWylogowania: string | null;
  wylogowanoRecznie: boolean;
}

const KLUCZ = 'bramka.sesja';
const Kontekst = createContext<KontekstAuth | null>(null);

function wczytajSesje(): Sesja | null {
  try {
    const s = sessionStorage.getItem(KLUCZ);
    if (!s) return null;
    const sesja = JSON.parse(s) as Sesja;
    if (new Date(sesja.wygasa).getTime() <= Date.now()) return null;
    return sesja;
  } catch {
    return null;
  }
}

function zapiszSesje(sesja: Sesja | null) {
  try {
    if (sesja) sessionStorage.setItem(KLUCZ, JSON.stringify(sesja));
    else sessionStorage.removeItem(KLUCZ);
  } catch {
    /* tryb prywatny: sesja zostaje tylko w pamięci */
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [sesja, setSesja] = useState<Sesja | null>(() => {
    const s = wczytajSesje();
    ustawToken(s?.token ?? null);
    return s;
  });
  const [komunikatWylogowania, setKomunikat] = useState<string | null>(null);
  const [wylogowanoRecznie, setWylogowanoRecznie] = useState(false);

  const zakoncz = useCallback(
    (komunikat: string | null, recznie = false) => {
      ustawToken(null);
      zapiszSesje(null);
      zapiszKluczSymulatora(null);
      setSesja(null);
      setKomunikat(komunikat);
      setWylogowanoRecznie(recznie);
      queryClient.clear();
    },
    [queryClient],
  );

  // 401 na dowolnym żądaniu = token nieważny, wracamy do logowania.
  useEffect(() => {
    przyBrakuUwierzytelnienia(() => zakoncz('Sesja wygasła. Zaloguj się ponownie.'));
    return () => przyBrakuUwierzytelnienia(null);
  }, [zakoncz]);

  // Token JWT jest ważny 30 minut, po tym czasie wylogowujemy automatycznie.
  useEffect(() => {
    if (!sesja) return;
    const zostalo = new Date(sesja.wygasa).getTime() - Date.now();
    const t = window.setTimeout(() => zakoncz('Sesja wygasła (30 min). Zaloguj się ponownie.'), Math.max(zostalo, 0));
    return () => window.clearTimeout(t);
  }, [sesja, zakoncz]);

  // Po odświeżeniu strony potwierdzamy, że token jest dalej ważny, i pobieramy aktualną rolę.
  useEffect(() => {
    if (!sesja) return;
    api
      .get<Uzytkownik>('/auth/ja')
      .then(({ dane }) => {
        setSesja((s) => {
          if (!s) return s;
          const nowa = { ...s, uzytkownik: dane };
          zapiszSesje(nowa);
          return nowa;
        });
      })
      .catch(() => {
        /* 401 obsługuje przyBrakuUwierzytelnienia, inne błędy nie wylogowują */
      });
    // tylko przy starcie aplikacji
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const zaloguj = useCallback(async (login: string, haslo: string) => {
    const { dane } = await api.logowanie<TokenLogowania>({ login, haslo });
    const nowa: Sesja = { token: dane.token, wygasa: dane.wygasa, uzytkownik: dane.uzytkownik };
    ustawToken(nowa.token);
    zapiszSesje(nowa);
    setKomunikat(null);
    setWylogowanoRecznie(false);
    setSesja(nowa);
  }, []);

  const wyloguj = useCallback(async () => {
    try {
      await api.wylogowanie();
    } catch {
      /* token i tak usuwamy lokalnie */
    }
    zakoncz(null, true);
  }, [zakoncz]);

  const wartosc = useMemo<KontekstAuth>(() => {
    const u = sesja?.uzytkownik ?? null;
    return {
      uzytkownik: u,
      czyAdmin: u?.rola === 'ADMIN',
      czyOperator: u?.rola === 'OPERATOR',
      siec: u?.siec ?? null,
      zaloguj,
      wyloguj,
      komunikatWylogowania,
      wylogowanoRecznie,
    };
  }, [sesja, zaloguj, wyloguj, komunikatWylogowania, wylogowanoRecznie]);

  return <Kontekst.Provider value={wartosc}>{children}</Kontekst.Provider>;
}

export function useAuth(): KontekstAuth {
  const k = useContext(Kontekst);
  if (!k) throw new Error('useAuth poza AuthProvider');
  return k;
}
