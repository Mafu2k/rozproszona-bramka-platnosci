import { useState, type FormEvent } from 'react';
import { Eye, EyeSlash } from '@phosphor-icons/react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { BladApi, Pole } from '../components/ui';
import { Logo } from '../components/Uklad';

export function Logowanie() {
  const { uzytkownik, zaloguj, komunikatWylogowania } = useAuth();
  const location = useLocation();
  const [login, setLogin] = useState('');
  const [haslo, setHaslo] = useState('');
  const [blad, setBlad] = useState<unknown>(null);
  const [trwa, setTrwa] = useState(false);
  const [pokazHaslo, setPokazHaslo] = useState(false);

  if (uzytkownik) {
    const cel = (location.state as { z?: string } | null)?.z ?? '/';
    return <Navigate to={cel} replace />;
  }

  async function wyslij(e: FormEvent) {
    e.preventDefault();
    setTrwa(true);
    setBlad(null);
    try {
      await zaloguj(login.trim(), haslo);
    } catch (err) {
      setBlad(err);
    } finally {
      setTrwa(false);
    }
  }

  return (
    <div className="logowanie">
      <form className="karta-logowania" onSubmit={wyslij}>
        <Logo podpis="rozproszona baza Visa · MasterCard · AmEx" />
        <h1>Logowanie do panelu</h1>
        {komunikatWylogowania && <div className="baner baner-info">{komunikatWylogowania}</div>}
        <BladApi blad={blad} />
        <Pole etykieta="Login">
          <input value={login} onChange={(e) => setLogin(e.target.value)} autoComplete="username" required autoFocus />
        </Pole>
        <Pole etykieta="Hasło">
          <span className="pole-z-przyciskiem">
            <input
              type={pokazHaslo ? 'text' : 'password'}
              value={haslo}
              onChange={(e) => setHaslo(e.target.value)}
              autoComplete="current-password"
              required
            />
            <button
              type="button"
              className="przycisk-ikona"
              onClick={() => setPokazHaslo((v) => !v)}
              aria-label={pokazHaslo ? 'Ukryj hasło' : 'Pokaż hasło'}
              aria-pressed={pokazHaslo}
            >
              {pokazHaslo ? <EyeSlash aria-hidden /> : <Eye aria-hidden />}
            </button>
          </span>
        </Pole>
        <button type="submit" className="przycisk przycisk-glowny przycisk-pelny" disabled={trwa}>
          {trwa ? 'Logowanie…' : 'Zaloguj'}
        </button>
        <p className="maly wyciszony">
          Konta demo: <code>admin</code>, <code>operator.visa</code>, <code>operator.mastercard</code>,{' '}
          <code>operator.amex</code>. Hasła są w pliku <code>.env</code> backendu.
        </p>
      </form>
    </div>
  );
}
