# Notatka dla frontendu: API panelu `v1`

Kontrakt: [`api/openapi.yaml`](../api/openapi.yaml), wersja `1.0.0`. Nazwy pól, typy, kody błędów i przykłady odpowiedzi są w kontrakcie.

## Na czym pracować

| Tryb | Uruchomienie | Adres bazowy |
|---|---|---|
| Mock z kontraktu (bez baz i Javy) | `docker compose --profile mock up -d mock-api` | `http://localhost:4010` |
| Prawdziwy backend | `docker compose --profile aplikacja up -d --build` | `http://localhost:8080/api/v1` |

Mock zwraca przykłady z kontraktu i waliduje żądania (np. brak wymaganego pola daje 400). Wybór przykładu: nagłówek `Prefer: example=odrzucona`, wymuszenie statusu: `Prefer: code=409`.

CORS backendu przepuszcza `http://localhost:5173`, `http://localhost:3000` i `http://localhost:4200`. Inny adres ustawia się zmienną `CORS_ORIGINS` w `.env`.

## Logowanie

1. `POST /auth/logowanie` z `{ "login", "haslo" }` zwraca `token` (JWT, ważny 30 min) i dane użytkownika.
2. Każde kolejne żądanie wysyła nagłówek `Authorization: Bearer <token>`.
3. `GET /auth/ja` zwraca `{ id, login, rola, siec }`: rola `ADMIN` albo `OPERATOR`, a `siec` to `VISA`, `MC` albo `AMEX` (dla administratora `null`).
4. `POST /auth/wylogowanie` unieważnia token. 401 oznacza powrót do ekranu logowania.

Po 5 nieudanych próbach logowania w ciągu 5 minut przychodzi 429 z nagłówkiem `Retry-After`.

## Co widzi która rola

Uprawnienia sprawdza backend, UI tylko ukrywa przyciski.

| Obszar | ADMIN | OPERATOR |
|---|---|---|
| Klienci: lista i szczegóły | tak | tak (do wydawania kart) |
| Klienci: dodanie, edycja, usunięcie, blokada globalna | tak | nie |
| Sklepy: lista i szczegóły | tak | tak |
| Sklepy: dodanie, edycja, usunięcie, klucz API | tak | nie |
| Karty: lista i szczegóły | wszystkie sieci | tylko swoja sieć |
| Karty: wydanie, limit, blokada, odblokowanie | nie | tylko swoja sieć |
| Transakcje | wszystkie sieci | tylko swoja sieć |
| Raporty (FR9, FR10), stan węzłów (FR11) | tak | nie |

Karta albo transakcja innej sieci daje operatorowi 403.

## Zapisy: nagłówek `Idempotency-Key`

Każdy `POST`, `PUT` i `DELETE` w panelu wymaga nagłówka `Idempotency-Key` (8–64 znaki, np. UUID).

- Nowy klucz generujesz przy każdej akcji użytkownika (np. kliknięciu „Zapisz”).
- Przy ponowieniu tej samej akcji (timeout, utrata sieci) wysyłasz ten sam klucz. Backend zwróci zapisaną odpowiedź i nie wykona operacji drugi raz.
- Ten sam klucz z innymi danymi daje 409 z kodem `94`.

## Konwencje danych

- Kwoty: liczby całkowite w groszach (`12999` = 129,99 zł). Formatowanie do złotych tylko w UI.
- Daty i czasy: ISO 8601 w UTC (`2026-11-13T12:30:00.000Z`). Do wyświetlenia przelicz na czas lokalny.
- Data ważności karty: `RRRR-MM`.
- Numer karty w odpowiedziach to zawsze maska (`5555554444`). Pełny numer pojawia się tylko raz, w odpowiedzi na wydanie karty (`POST /karty`, pole `numerKarty`). Trzeba go pokazać od razu, bo powtórzone żądanie zwraca już `null`.
- Klucz API sklepu z `POST /sklepy/{id}/klucz-api` też jest pokazywany tylko raz.
- Stronicowanie: parametry `strona` (od 0) i `rozmiar` (1–100), odpowiedź `{ elementy, strona, rozmiar, lacznie, ... }`.
- Szczegóły transakcji (`GET /transakcje/{id}`): pole `pierwotna` występuje tylko wtedy, gdy transakcja jest zwrotem.

## Błędy

Każdy błąd ma format `{ "kod": "...", "komunikat": "...", "szczegoly": { ... } | null }`.

| HTTP | `kod` | Co pokazać |
|---|---|---|
| 400 | `WALIDACJA` | błędy pól z `szczegoly` (nazwa pola: komunikat) |
| 400 | `BRAK_KLUCZA_IDEMPOTENCJI` | błąd po stronie UI, brak nagłówka |
| 401 | `BRAK_UWIERZYTELNIENIA`, `NIEPRAWIDLOWE_DANE_LOGOWANIA` | ekran logowania |
| 403 | `BRAK_UPRAWNIEN` | brak dostępu |
| 404 | `NIE_ZNALEZIONO` | brak rekordu |
| 409 | `94` | „to żądanie zostało już wysłane z innymi danymi” |
| 409 | `REPLIKACJA_W_TOKU` | „dane jeszcze się synchronizują”, ponów po `Retry-After` sekundach (zwykle do 30 s) |
| 409 | `KLIENT_MA_AKTYWNE_KARTY`, `KLIENT_NIEAKTYWNY`, `LIMIT_PONIZEJ_WYKORZYSTANIA`, `DUPLIKAT` | komunikat z `komunikat` |
| 429 | `ZBYT_WIELE_PROB` | odczekaj `Retry-After` |
| 503 | `91` | „sieć `szczegoly.siec` niedostępna, nic nie zostało zmienione” |
| 500 | `96` | błąd systemu |

## Dane częściowe przy awarii węzła

Listy z wielu sieci (karty, transakcje administratora, karty klienta) i raporty mają pola `czesciowy` i `brakujaceSieci`. Gdy `czesciowy: true`, pokaż ostrzeżenie, np. „brak danych z sieci MC”; dane pozostałych sieci są poprawne.

Listy klientów i sklepów mają pole `zrodlo`. Wartość inna niż `VISA` oznacza, że węzeł główny jest niedostępny, a dane pochodzą z repliki (mogą być do 60 s starsze). Zapisy klientów i sklepów zwracają wtedy 503.

## Monitoring (FR11)

`GET /wezly/stan` jest lekkie (wynik buforowany przez 2 s), więc można je odpytywać co 5 s. Dla każdego węzła zwraca:
- `dostepny` i `czasOdpowiedziMs`,
- dla dostępnych replik obiekt `replikacja` z polami `ostatnieOdswiezenie`, `opoznienieSekund` i `przekroczonoLimit` (powyżej 60 s); dla węzła głównego i niedostępnych replik pola `replikacja` nie ma,
- dla Visy `logiMv` (zmiany czekające na repliki),
- `wiszaceTransakcje2pc`.

## Przykłady wywołań

```bash
TOKEN=$(curl -s http://localhost:8080/api/v1/auth/logowanie -H 'Content-Type: application/json' \
  -d '{"login":"operator.mastercard","haslo":"<PANEL_OPERATOR_PASSWORD>"}' | jq -r .token)

curl -s "http://localhost:8080/api/v1/klienci?szukaj=kowal&rozmiar=10" -H "Authorization: Bearer $TOKEN"

curl -s http://localhost:8080/api/v1/karty -X POST -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -H "Idempotency-Key: $(uuidgen)" \
  -d '{"idKlienta":1,"limitKredytowy":500000}'

curl -s "http://localhost:8080/api/v1/transakcje?sortuj=kwota&kierunek=desc&rozmiar=20" -H "Authorization: Bearer $TOKEN"
```

Płatność ze sklepu (klucz z `POST /sklepy/{id}/klucz-api`):

```bash
curl -s http://localhost:8080/api/v1/platnosci/autoryzacje -H "X-Api-Key: <klucz>" -H 'Content-Type: application/json' \
  -d '{"idZadania":"zam-1","numerKarty":"5555555555554444","kwota":12999,"waluta":"PLN"}'
```

## Lista zmian kontraktu

| Wersja | Zmiana | Niekompatybilna |
|---|---|---|
| 1.0.0 | Pierwsza wersja: FR1–FR11, konwencje, kody błędów, przykłady pod mock | — |
