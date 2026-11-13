# Panel bramki płatności (frontend)

React 18 + TypeScript + Vite. Panel korzysta z REST API backendu opisanego w [`../api/openapi.yaml`](../api/openapi.yaml) (zasady w [`../docs/notatka-dla-frontendu.md`](../docs/notatka-dla-frontendu.md)).

## Uruchomienie

Wymagany Node.js 20+.

```bash
cd frontend
npm install
```

| Tryb | Co uruchomić wcześniej | Komenda | Adres |
|---|---|---|---|
| Mock z kontraktu (bez Javy i baz) | `docker compose --profile mock up -d mock-api` albo `npx @stoplight/prism-cli@5 mock api/openapi.yaml -p 4010` | `npm run dev:mock` | http://localhost:5173 |
| Prawdziwy backend | `docker compose --profile aplikacja up -d --build` | `npm run dev` | http://localhost:5173 |

Front zawsze woła `/api/v1/...`, a Vite przekazuje żądania do backendu (`:8080`) albo mocka (`:4010`). Inny adres backendu ustawisz w `frontend/.env.local`: `API_TARGET=http://host:port`.

Na mocku można zalogować się dowolnym loginem i hasłem. Na backendzie używasz kont `admin`, `operator.visa`, `operator.mastercard`, `operator.amex` z hasłami z `.env`.

Build produkcyjny: `npm run build` (wynik w `dist/`).

## Ekrany

| Ekran | Wymaganie | Rola | Co pokazuje |
|---|---|---|---|
| Logowanie | FR1 | wszyscy | JWT, wylogowanie po 30 min albo po 401 |
| Klienci + szczegóły klienta | FR4, FR7 | admin: CRUD, blokada globalna; operator: odczyt | CRUD, karty klienta ze wszystkich węzłów, blokada 2PC |
| Sklepy | FR5 | admin: CRUD i klucz API; operator: odczyt | CRUD, jednorazowy klucz API |
| Karty | FR6 | operator: wydanie, limit, blokada; admin: odczyt | fragmentacja według sieci, pełny numer pokazany raz |
| Transakcje | FR8 | wszyscy (operator tylko swoja sieć) | filtry, sortowanie, szczegóły ze zwrotami |
| Obroty sklepu | FR9 | admin | zapytanie do 3 węzłów + suma |
| Statystyki sieci | FR10 | admin | COUNT, SUM, % odrzuceń, wykresy |
| Stan węzłów | FR11 | admin | dostępność, opóźnienie replik, 2PC, historia 3 min |
| Symulator sklepu | FR2, FR3 | wszyscy (wymaga klucza API sklepu) | płatność, zwrot, idempotencja, test obciążenia |

## Mechanizmy wspólne

- **`Idempotency-Key`** – każdy zapis z panelu ma klucz (UUID) tworzony razem z formularzem. Po błędzie sieci albo 5xx ponowienie używa tego samego klucza, po sukcesie powstaje nowy (`src/lib/useZapis.ts`).
- **Błędy** – format `{ kod, komunikat, szczegoly }` tłumaczony na komunikaty (`src/lib/bledy.ts`), błędy walidacji pod polami formularza.
- **Awaria węzła** – `czesciowy`/`brakujaceSieci` dają baner „brak danych z sieci …”, a `zrodlo` ≠ `VISA` baner „odczyt z repliki” i blokadę przycisków zapisu.
- **Kwoty** w groszach, **daty** w UTC – przeliczane tylko do wyświetlenia (`src/lib/format.ts`).
- **Czas odpowiedzi** każdego zapytania widać przy listach i raportach (do zrzutów w testach).

## Pod sprawozdanie (sekcje 5 i 6)

| Punkt | Gdzie w panelu |
|---|---|
| 5: wyszukanie rekordu, filtrowanie | Klienci (szukaj), Transakcje (RRN, status, kwota, daty) |
| 5: dodanie, aktualizacja, usunięcie | Klienci / Sklepy: Dodaj, Edytuj, Usuń; Karty: limit |
| 5: dane z dwóch i więcej węzłów | Szczegóły klienta (karty ze wszystkich sieci), Transakcje admina bez filtra sieci |
| 5: agregacja, zestawienie całego systemu | Obroty sklepu, Statystyki sieci |
| 6.2: obciążenie (1 / 10 / 100 zapytań) | Symulator → Test obciążenia (tabela wyników) |
| 6.3: spójność replik | edycja klienta + Stan węzłów (opóźnienie replik, logi MV) |
| 6.4: awaria węzła | `docker stop mastercard-db` + Stan węzłów, banery na listach, kod 91 w symulatorze |

## Wygląd

System wizualny dobrany wtyczką ui-ux-pro-max: styl Swiss Minimalism, paleta „Banking / Traditional Finance” (granat i stonowane złoto) z ciemnym motywem, fonty IBM Plex Sans / Mono (lokalnie, bez internetu), ikony Phosphor, gęsty układ dashboardu. Kolory są tokenami w `src/styles.css`. Jest widoczny fokus, `prefers-reduced-motion`, boczne menu jako szuflada poniżej 1024 px i cele dotyku 44 px na telefonie.
