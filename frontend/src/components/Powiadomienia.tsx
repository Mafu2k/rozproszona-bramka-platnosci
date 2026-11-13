import { createContext, useCallback, useContext, useState, type ReactNode } from 'react';

type Typ = 'sukces' | 'blad' | 'info';
interface Powiadomienie {
  id: number;
  tekst: string;
  typ: Typ;
}

const Kontekst = createContext<(tekst: string, typ?: Typ) => void>(() => {});

export function PowiadomieniaProvider({ children }: { children: ReactNode }) {
  const [lista, setLista] = useState<Powiadomienie[]>([]);

  const pokaz = useCallback((tekst: string, typ: Typ = 'sukces') => {
    const id = Date.now() + Math.random();
    setLista((l) => [...l, { id, tekst, typ }]);
    window.setTimeout(() => setLista((l) => l.filter((p) => p.id !== id)), 5000);
  }, []);

  return (
    <Kontekst.Provider value={pokaz}>
      {children}
      <div className="powiadomienia" aria-live="polite">
        {lista.map((p) => (
          <div key={p.id} className={`powiadomienie powiadomienie-${p.typ}`}>
            {p.tekst}
          </div>
        ))}
      </div>
    </Kontekst.Provider>
  );
}

export const usePowiadomienie = () => useContext(Kontekst);
