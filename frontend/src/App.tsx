import type { ReactNode } from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { useAuth } from './auth/AuthContext';
import { Uklad } from './components/Uklad';
import { Pusto } from './components/ui';
import { Karty } from './pages/Karty';
import { Klienci } from './pages/Klienci';
import { KlientSzczegoly } from './pages/KlientSzczegoly';
import { Logowanie } from './pages/Logowanie';
import { RaportObrotowSklepu, StatystykiSieciStrona } from './pages/Raporty';
import { Sklepy } from './pages/Sklepy';
import { StanWezlowStrona } from './pages/StanWezlow';
import { Symulator } from './pages/Symulator';
import { Transakcje } from './pages/Transakcje';

/** Trasy tylko dla zalogowanych. Uprawnienia i tak sprawdza backend; tu tylko nie pokazujemy ekranów bez dostępu. */
function Chroniona({ children, tylkoAdmin }: { children: ReactNode; tylkoAdmin?: boolean }) {
  const { uzytkownik, czyAdmin, wylogowanoRecznie } = useAuth();
  const location = useLocation();
  if (!uzytkownik) {
    const powrot = wylogowanoRecznie ? undefined : { z: location.pathname + location.search };
    return <Navigate to="/logowanie" replace state={powrot} />;
  }
  if (tylkoAdmin && !czyAdmin) {
    return <Pusto>Ten ekran jest dostępny tylko dla administratora bramki.</Pusto>;
  }
  return <>{children}</>;
}

function StronaStartowa() {
  const { czyAdmin } = useAuth();
  return <Navigate to={czyAdmin ? '/wezly' : '/karty'} replace />;
}

export function App() {
  return (
    <Routes>
      <Route path="/logowanie" element={<Logowanie />} />
      <Route
        element={
          <Chroniona>
            <Uklad />
          </Chroniona>
        }
      >
        <Route index element={<StronaStartowa />} />
        <Route path="klienci" element={<Klienci />} />
        <Route path="klienci/:id" element={<KlientSzczegoly />} />
        <Route path="sklepy" element={<Sklepy />} />
        <Route path="karty" element={<Karty />} />
        <Route path="transakcje" element={<Transakcje />} />
        <Route
          path="raporty/obroty-sklepu"
          element={
            <Chroniona tylkoAdmin>
              <RaportObrotowSklepu />
            </Chroniona>
          }
        />
        <Route
          path="raporty/statystyki-sieci"
          element={
            <Chroniona tylkoAdmin>
              <StatystykiSieciStrona />
            </Chroniona>
          }
        />
        <Route
          path="wezly"
          element={
            <Chroniona tylkoAdmin>
              <StanWezlowStrona />
            </Chroniona>
          }
        />
        <Route path="symulator" element={<Symulator />} />
        <Route path="*" element={<Pusto>Nie ma takiej strony.</Pusto>} />
      </Route>
    </Routes>
  );
}
