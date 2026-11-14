# rozproszona-bramka-platnosci

Rozproszona baza danych bramki płatności internetowych z symulowaną autoryzacją kart, aplikacja w Javie, która z niej korzysta, oraz panel webowy w React.

Projekt z przedmiotu Rozproszone bazy danych · Bartosz Jojko, Łukasz Janicki.

## Spis treści

- [O projekcie](#o-projekcie)
- [Struktura repozytorium](#struktura-repozytorium)
- [Szybki start](#szybki-start)
- [Uruchomienie krok po kroku](#uruchomienie-krok-po-kroku)
- [Adresy i konta](#adresy-i-konta)
- [Jak przetestować system](#jak-przetestować-system)
- [Monitoring i scenariusz obrony](#monitoring-i-scenariusz-obrony)
- [Zatrzymanie i reset](#zatrzymanie-i-reset)
- [Rozwiązywanie problemów](#rozwiązywanie-problemów)
- [Dla programistów](#dla-programistów)

## O projekcie

Bramka przyjmuje od sklepów płatności kartą, rozpoznaje sieć kartową po numerze BIN i autoryzuje transakcję w bazie właściwej sieci. Każda sieć to osobny węzeł Oracle:

| Węzeł | Sieć | Fragmenty lokalne | Repliki |
|:---:|---|---|---|
| `1` `visa` | Visa | Karta i transakcja Visa | Kopia główna: klient, sklep, siec_kartowa, zakres_bin, uzytkownik |
| `2` `mastercard` | MasterCard | Karta i transakcja MasterCard | Widoki zmaterializowane (REFRESH FAST co 30 s): klient, sklep, siec_kartowa, zakres_bin, uzytkownik |
| `3` `amex` | American Express | Karta i transakcja American Express | Widoki zmaterializowane (REFRESH FAST co 30 s): klient, sklep, siec_kartowa, zakres_bin, uzytkownik |

```
 Sklep / Panel (React) ──► Backend (Spring Boot) ──┬──► visa-db       (węzeł 1, kopia główna)
                                                   ├──► mastercard-db (węzeł 2, replika + fragment MC)
                                                   └──► amex-db       (węzeł 3, replika + fragment AmEx)
                                  węzły połączone database linkami (replikacja, 2PC)
```

## Struktura repozytorium

```
api/openapi.yaml               kontrakt REST /api/v1 dla frontendu i sklepów
backend/                       Spring Boot 4, Java 21: bramka, API panelu, serwer ISO 8583 (jPOS)
database/
  init/                        użytkownicy bazy tworzeni przy pierwszym starcie węzła
  migrations/visa/             dane wspólne (kopia główna), logi widoków zmaterializowanych, klucze obce
  migrations/repliki/          łącze do Visy, widoki zmaterializowane i ich odświeżanie (MC, AmEx, kolejne sieci)
  migrations/fragmenty/        karta, transakcja, pakiety PL/SQL autoryzacji i kart (każdy węzeł)
  migrations/visa-polaczenia/  łącza do węzłów sieci, widoki globalne, blokada i usuwanie klienta (2PC)
  seed/visa/                   dane demonstracyjne (klienci, sklepy)
  queries/                     przykładowe zapytania rozproszone
  wycofanie/                   skrypty wycofania migracji
demonstracja/                  scenariusz obrony jedną komendą, konfiguracja czwartej sieci
docs/                          raport, diagramy, notatki
frontend/                      panel webowy: React 18 + TypeScript + Vite
monitoring/                    Prometheus i Grafana z gotowym pulpitem
docker-compose.yml             węzły Oracle, migracje, backend (profil aplikacja), mock API (profil mock),
                               monitoring (profil monitoring), generator ruchu (profil ruch)
```

## Szybki start

Gdy masz już Dockera, Node.js i plik `.env` (patrz niżej):

```powershell
docker compose --profile aplikacja up -d --build    # bazy + migracje + backend (kilka minut za 1. razem)
cd frontend
npm install                                         # tylko za pierwszym razem
npm run dev                                         # panel: http://localhost:5173
```

Logowanie: `admin` z hasłem `PANEL_ADMIN_PASSWORD` z pliku `.env`.

## Uruchomienie krok po kroku

### 1. Wymagania

| Narzędzie | Do czego | Sprawdzenie |
|---|---|---|
| Git | pobranie projektu | `git --version` |
| Docker Desktop **albo** Docker w WSL2 | węzły Oracle, migracje, backend | `docker compose version` |
| Node.js 20+ | panel webowy | `node -v` |
| ok. 8 GB wolnego RAM | trzy instancje Oracle naraz | — |

**Windows: Docker Desktop (najprościej).** Zainstaluj ze strony docker.com i uruchom. Ikonka wieloryba w zasobniku musi być zielona, zanim wpiszesz jakąkolwiek komendę `docker`.

**Windows: Docker w WSL2 (bez Docker Desktop).** PowerShell jako administrator, potem restart:

```powershell
wsl --install
```

W Ubuntu (`wsl`):

```bash
sudo apt update
sudo apt install -y docker.io docker-compose-v2
sudo usermod -aG docker $USER
exit
```

```powershell
wsl --shutdown
```

**macOS / Linux:** Docker Desktop albo `docker.io` + `docker-compose-v2`.

### 2. Pobranie projektu

```bash
git clone https://github.com/Mafu2k/rozproszona-bramka-platnosci.git
cd rozproszona-bramka-platnosci
git checkout develop
```

### 3. Plik `.env` z hasłami

Każdy tworzy własny `.env` z losowymi hasłami. Plik jest w `.gitignore`, więc nie trafia do repozytorium i nie wysyła się go innym.

**Git Bash / WSL / macOS / Linux:**

```bash
{
  for v in VISA_ORACLE_PASSWORD MASTERCARD_ORACLE_PASSWORD AMEX_ORACLE_PASSWORD BRAMKA_PASSWORD LINK_PASSWORD PANEL_ADMIN_PASSWORD PANEL_OPERATOR_PASSWORD; do echo "$v=P$(openssl rand -hex 12)"; done
  for v in KARTA_HMAC_KEY JWT_SECRET; do echo "$v=$(openssl rand -hex 32)"; done
} > .env
```

**PowerShell:**

```powershell
function hex($n) { $b = New-Object byte[] $n; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); -join ($b | ForEach-Object { $_.ToString('x2') }) }
$linie = foreach ($v in 'VISA_ORACLE_PASSWORD','MASTERCARD_ORACLE_PASSWORD','AMEX_ORACLE_PASSWORD','BRAMKA_PASSWORD','LINK_PASSWORD','PANEL_ADMIN_PASSWORD','PANEL_OPERATOR_PASSWORD') { "$v=P$(hex 12)" }
$linie += foreach ($v in 'KARTA_HMAC_KEY','JWT_SECRET') { "$v=$(hex 32)" }
$linie | Set-Content -Encoding ascii .env
```

Hasła do panelu sprawdzisz komendą `cat .env` (PowerShell: `Get-Content .env`).

| Zmienna | Do czego |
|---|---|
| `PANEL_ADMIN_PASSWORD` | hasło konta `admin` w panelu |
| `PANEL_OPERATOR_PASSWORD` | hasło kont `operator.visa`, `operator.mastercard`, `operator.amex` |
| `BRAMKA_PASSWORD` | użytkownik `bramka` w każdym węźle (np. DBeaver, sqlplus) |
| `VISA_/MASTERCARD_/AMEX_ORACLE_PASSWORD` | administrator Oracle w danym węźle |
| `LINK_PASSWORD` | łącza bazodanowe między węzłami |
| `KARTA_HMAC_KEY`, `JWT_SECRET` | klucze backendu (tokeny kart, logowanie) |

Opcjonalnie: `RUCH_TPS` i `RUCH_UDZIAL_ISO` sterują generatorem ruchu (domyślne wartości są w `.env.template`).

### 4. Bazy, migracje i backend

```powershell
docker compose --profile aplikacja up -d --build
docker compose --profile aplikacja ps -a
```

Pierwszy start trwa kilka minut (pobieranie obrazów Oracle i budowa backendu). Wszystko jest gotowe, gdy:

| Kontener | Oczekiwany status |
|---|---|
| `visa-db`, `mastercard-db`, `amex-db` | `Up … (healthy)` |
| `migracje-visa`, `migracje-mastercard`, `migracje-amex`, `migracje-visa-polaczenia` | `Exited (0)` |
| `backend` | `Up` |

Szybki test backendu: http://localhost:8080/api/v1/auth/ja powinno zwrócić błąd 401 (backend działa, tylko nie jesteś zalogowany).

Inne warianty startu:

| Co | Komenda | Adres |
|---|---|---|
| Tylko węzły i migracje | `docker compose up -d` | Oracle `localhost:1521-1523` |
| Węzły, migracje i backend | `docker compose --profile aplikacja up -d --build` | API `http://localhost:8080/api/v1`, ISO 8583 `localhost:8583` |
| Mock API z kontraktu | `docker compose --profile mock up -d mock-api` | `http://localhost:4010` |
| Monitoring | `docker compose --profile aplikacja --profile monitoring up -d --build` | Grafana `http://localhost:3001`, Prometheus `http://localhost:9090` |
| Generator ruchu | `docker compose --profile ruch up -d --build generator-ruchu` | `docker logs -f generator-ruchu` |

### 5. Panel webowy

```powershell
cd frontend
npm install        # tylko za pierwszym razem
npm run dev
```

Otwórz http://localhost:5173 i zaloguj się (konta niżej). Panel przekazuje żądania do backendu na `localhost:8080`, więc backend musi działać.

**Panel bez baz i bez Javy** (do oglądania ekranów, dane są przykładowe z kontraktu):

```powershell
# okno 1
npx @stoplight/prism-cli@5 mock api/openapi.yaml -p 4010
# okno 2
cd frontend
npm run dev:mock
```

Na mocku można się zalogować dowolnym loginem i hasłem. Więcej o panelu: [`frontend/README.md`](frontend/README.md).

## Adresy i konta

| Usługa | Adres |
|---|---|
| Panel | http://localhost:5173 |
| API backendu | http://localhost:8080/api/v1 |
| Kontrakt API | [`api/openapi.yaml`](api/openapi.yaml) |
| Grafana (profil monitoring) | http://localhost:3001 |
| Prometheus (profil monitoring) | http://localhost:9090 |

### Konta panelu

| Login | Rola | Hasło | Co może |
|---|---|---|---|
| `admin` | administrator | `PANEL_ADMIN_PASSWORD` | klienci i sklepy (CRUD), blokada globalna, raporty, stan węzłów |
| `operator.visa` | operator Visa | `PANEL_OPERATOR_PASSWORD` | karty i transakcje sieci Visa |
| `operator.mastercard` | operator MasterCard | `PANEL_OPERATOR_PASSWORD` | karty i transakcje sieci MasterCard |
| `operator.amex` | operator AmEx | `PANEL_OPERATOR_PASSWORD` | karty i transakcje sieci American Express |

### Dane demo

Backend przy pierwszym starcie wydaje karty demo z publicznych numerów testowych i kilka transakcji:

| Sieć | Numery kart |
|---|---|
| Visa | `4111111111111111`, `4012888888881881` |
| MasterCard | `5555555555554444`, `5105105105105100`, `2223003122003222` |
| American Express | `378282246310005`, `371449635398431` |

Klucz API sklepu generuje administrator: w panelu **Sklepy → Klucz API** albo `POST /api/v1/sklepy/{id}/klucz-api`.

### Połączenie z bazą

| Węzeł | Host | Service | User | Hasło |
|---|---|---|---|---|
| visa | `localhost:1521` | `FREEPDB1` | `bramka` | `BRAMKA_PASSWORD` z `.env` |
| mastercard | `localhost:1522` | `FREEPDB1` | `bramka` | `BRAMKA_PASSWORD` z `.env` |
| amex | `localhost:1523` | `FREEPDB1` | `bramka` | `BRAMKA_PASSWORD` z `.env` |

```bash
docker exec -it visa-db sqlplus bramka@//localhost/FREEPDB1
```

Jeśli port 1521 jest zajęty na Twoim komputerze, Visa może być na innym porcie, patrz [Rozwiązywanie problemów](#rozwiązywanie-problemów).

## Jak przetestować system

Wszystko poniżej da się wyklikać w panelu. Przy listach i raportach panel pokazuje czas odpowiedzi, co przydaje się do zrzutów ekranu do sprawozdania.

### Operacje podstawowe

| Co | Konto | Gdzie w panelu |
|---|---|---|
| CREATE / READ / UPDATE / DELETE | `admin` | **Klienci** i **Sklepy**: Dodaj, Szukaj, Edytuj, Usuń |
| Wydanie karty, limit, blokada | `operator.*` | **Karty → Wydaj kartę**, przyciski Limit / Zablokuj |
| Płatność kartą (FR2) | dowolne | **Sklepy → Klucz API → Użyj w symulatorze**, potem **Symulator sklepu → Zapłać** |
| Zwrot (FR3) | dowolne | **Symulator sklepu**, przycisk „Zwrot” przy zatwierdzonej płatności |
| Dane z kilku węzłów | `admin` | **Klienci → szczegóły** (karty ze wszystkich sieci), **Transakcje** bez filtra sieci |
| Agregacje (FR9, FR10) | `admin` | **Obroty sklepu**, **Statystyki sieci** |
| Blokada we wszystkich sieciach (FR7, 2PC) | `admin` | **Klienci → szczegóły → Zablokuj we wszystkich sieciach** |

### Testy systemu rozproszonego

| Test | Jak wykonać | Czego się spodziewać |
|---|---|---|
| Obciążenie (6.2) | **Symulator → Test obciążenia**: 1, 10, 100 zapytań | tabela: czas całkowity, średni, maks., liczba błędów |
| Spójność (6.3) | edytuj klienta, obserwuj **Stan węzłów** | zmiana na replikach MC i AmEx w ciągu ok. 30 s, opóźnienie widoczne przy węzłach |
| Awaria (6.4) | `docker stop mastercard-db` | węzeł czerwony w **Stanie węzłów**, baner „brak danych z sieci MasterCard” na listach, płatność Visa działa, MasterCard dostaje kod 91, blokada globalna wycofana |
| Powrót po awarii | `docker start mastercard-db` | węzeł wraca na zielono (widać na historii dostępności), repliki się odświeżają, 0 wiszących transakcji 2PC |

## Monitoring i scenariusz obrony

Grafana pokazuje pulpit bramki bez logowania (tylko odczyt). Pełny ekran: `http://localhost:3001/d/bramka?kiosk`.

Generator ruchu zakłada przez API 3 sklepy i 21 kart i płaci nimi ok. 20 razy na sekundę (20% przez ISO 8583). Część kart ma niski limit, jest zablokowana albo w EUR, więc pojawiają się odmowy 51, 62, 57 i 14. Tempo ustawiają `RUCH_TPS` i `RUCH_UDZIAL_ISO` w `.env`. Zatrzymanie: `docker compose stop generator-ruchu`.

Scenariusz obrony, z katalogu projektu w Ubuntu (WSL), Linuksie albo macOS (wymaga `python3`; Git Bash nie jest obsługiwany):

```bash
./demonstracja/scenariusz-obrony.sh
```

Sam uruchamia backend, monitoring i generator, potem czeka na Enter przed każdym krokiem:

1. normalna praca i stan węzłów (FR11)
2. zatrzymanie węzła Visa: MC i AmEx dalej autoryzują, Visa dostaje 91 (NFR3)
3. odcięcie MasterCard od sieci, zmiany na węźle 1, po podłączeniu replika nadrabia różnicę (NFR5)
4. blokada klienta we wszystkich sieciach w 2PC (FR7, NFR4)
5. czwarta sieć Discover jako nowy węzeł z tych samych migracji (NFR7), na końcu usuwana

`--auto` idzie bez zatrzymywania, numery wybierają kroki: `./demonstracja/scenariusz-obrony.sh 2 3`. Ctrl+C przywraca zatrzymane węzły.

## Zatrzymanie i reset

| Co | Komenda |
|---|---|
| Zatrzymanie panelu | `Ctrl+C` w oknie z `npm run dev` |
| Zatrzymanie kontenerów (dane zostają) | `docker compose --profile aplikacja down` |
| Ponowny start | `docker compose --profile aplikacja up -d` |
| Reset baz od zera (kasuje dane) | `docker compose down -v` |

## Rozwiązywanie problemów

**`ports are not available ... 127.0.0.1:1521 ... bind: An attempt was made to access a socket in a way forbidden`**

Port 1521 zajmuje inny program, najczęściej lokalnie zainstalowana baza Oracle (usługa `OracleOraDB21Home1TNSListener` / `OracleServiceXE`). Sprawdzenie w PowerShell:

```powershell
netstat -ano | Select-String ":1521\s"
```

Rozwiązanie A, bez zmian w systemie: utwórz w katalogu projektu plik `docker-compose.override.yml` (jest w `.gitignore`, Docker wczytuje go automatycznie):

```yaml
services:
  visa-db:
    ports: !override
      - "127.0.0.1:1524:1521"
```

Węzły łączą się między sobą po nazwach (`visa-db:1521`), więc zmienia się tylko adres do połączenia z Visą z Twojego komputera: `localhost:1524`.

Rozwiązanie B: zatrzymaj lokalną Oracle na czas projektu (PowerShell jako administrator). Zwolni to też sporo RAM-u:

```powershell
Stop-Service OracleServiceXE, OracleOraDB21Home1TNSListener
# ponowne uruchomienie: Start-Service OracleServiceXE, OracleOraDB21Home1TNSListener
```

**`failed to connect to the docker API ... dockerDesktopLinuxEngine`**: Docker Desktop nie jest uruchomiony. Włącz go i poczekaj, aż ikonka zrobi się zielona.

**`required variable LINK_PASSWORD is missing a value`** (albo inna zmienna): brak pliku `.env` albo jest niepełny. Wróć do kroku 3. Komendy `docker compose` trzeba uruchamiać z głównego katalogu projektu.

**Kontener `migracje-*` ma status `Exited (1)`**: podejrzyj błąd przez `docker logs migracje-visa`. Przy pierwszym uruchomieniu pomaga zwykle reset `docker compose down -v` i ponowny start.

**Panel pokazuje „Brak połączenia z serwerem API”**: backend nie działa. Sprawdź `docker compose --profile aplikacja ps` i `docker logs backend`.

**Port 5173 zajęty**: działa już inna kopia panelu (`npm run dev` albo `npm run dev:mock`). Zamknij ją przez `Ctrl+C`.

**Komputer bardzo zwalnia**: trzy instancje Oracle potrzebują dużo pamięci. Zamknij zbędne programy albo zatrzymaj lokalną Oracle (rozwiązanie B wyżej). W Docker Desktop: Settings → Resources, przydziel co najmniej 6–8 GB.

## Dla programistów

### Backend bez Dockera

Wymaga Javy 21. Czyta sekrety z `../.env` i łączy się z węzłami na `localhost:1521-1523`.

```bash
cd backend
./mvnw spring-boot:run
```

### Testy backendu

```bash
cd backend
./mvnw test
```

Testy integracyjne wymagają działających węzłów i backendu (np. profil `aplikacja`). Zatrzymują węzły i odłączają je od sieci, a po teście przywracają.

```bash
./mvnw -Pintegracja verify
```

Gdy Docker działa w WSL, a Maven na Windowsie: `./mvnw -Pintegracja verify -Ddocker.polecenie="wsl -d Ubuntu docker"`.

### Frontend

```bash
cd frontend
npm run dev        # z backendem na :8080
npm run dev:mock   # z mockiem na :4010
npm run build      # build produkcyjny do frontend/dist
```

Szczegóły (ekrany, idempotencja, obsługa awarii węzłów): [`frontend/README.md`](frontend/README.md).

### Praca z gitem

Gałąź robocza to `develop`. Nowe zmiany robimy na gałęziach `feature/...` i łączymy przez Pull Request do `develop`:

```bash
git switch develop
git pull
git switch -c feature/nazwa-zadania
```
