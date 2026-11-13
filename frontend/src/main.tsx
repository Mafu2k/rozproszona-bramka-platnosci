import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import '@fontsource/ibm-plex-sans/400.css';
import '@fontsource/ibm-plex-sans/500.css';
import '@fontsource/ibm-plex-sans/600.css';
import '@fontsource/ibm-plex-mono/400.css';
import '@fontsource/ibm-plex-mono/500.css';
import './styles.css';
import { ApiError } from './api/client';
import { AuthProvider } from './auth/AuthContext';
import { PowiadomieniaProvider } from './components/Powiadomienia';
import { App } from './App';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // Błędy 4xx (np. 403, 404) są odpowiedzią, a nie awarią, więc ich nie ponawiamy.
      retry: (proba, blad) => proba < 1 && !(blad instanceof ApiError && blad.status >= 400 && blad.status < 500),
      refetchOnWindowFocus: false,
      staleTime: 5_000,
    },
  },
});

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <PowiadomieniaProvider>
            <App />
          </PowiadomieniaProvider>
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
);
