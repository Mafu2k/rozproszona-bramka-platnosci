import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

// Front zawsze woła /api/v1, a Vite przekazuje żądania dalej (proxy), więc CORS nie przeszkadza.
//   npm run dev       -> prawdziwy backend (domyślnie http://localhost:8080)
//   npm run dev:mock  -> mock z kontraktu (Prism, http://localhost:4010, bez prefiksu /api/v1)
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '');
  const mock = mode === 'mock';
  const target = env.API_TARGET || (mock ? 'http://localhost:4010' : 'http://localhost:8080');

  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        '/api/v1': {
          target,
          changeOrigin: true,
          rewrite: mock ? (path) => path.replace(/^\/api\/v1/, '') : undefined,
        },
      },
    },
  };
});
