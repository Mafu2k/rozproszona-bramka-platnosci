import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import type { StanWezlow } from '../api/types';

export const INTERWAL_STANU_WEZLOW_MS = 5000;

export function useStanWezlow(wlaczone = true) {
  return useQuery({
    queryKey: ['wezly'],
    queryFn: () => api.get<StanWezlow>('/wezly/stan'),
    refetchInterval: INTERWAL_STANU_WEZLOW_MS,
    enabled: wlaczone,
  });
}
