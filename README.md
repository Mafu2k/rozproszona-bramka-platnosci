# rozproszona-bramka-platnosci


O projekcie

Rozproszona baza danych bramki płatności internetowych z symulowaną autoryzacją kart oraz aplikacja w Javie, która z niej korzysta.


| Węzeł | Sieć | Fragmenty lokalne | Repliki |
|:---:|---|---|---|
| `1` `visa` | Visa | Karta i transakcja Visa | Kopia główna: klient, sklep, siec_kartowa, zakres_bin, uzytkownik |
| `2` `mastercard` | MasterCard | Karta i transakcja MasterCard | Widoki zmaterializowane (REFRESH FAST co 30 s): klient, sklep, siec_kartowa, zakres_bin, uzytkownik |
| `3` `amex` | American Express | Karta i transakcja American Express | Widoki zmaterializowane (REFRESH FAST co 30 s): klient, sklep, siec_kartowa, zakres_bin, uzytkownik |


## Struktura

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
frontend/                      UI
monitoring/                    Prometheus i Grafana z gotowym pulpitem
docker-compose.yml             węzły Oracle, migracje, backend (profil aplikacja), mock API (profil mock),
                               monitoring (profil monitoring), generator ruchu (profil ruch)
```


## Uruchomienie

### 1. Docker (Windows, WSL2)

PowerShell jako administrator, potem restart:

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

macOS / Linux: Docker Desktop albo `docker.io` + `docker-compose-v2`.

### 2. Projekt

```bash
git clone https://github.com/Mafu2k/rozproszona-bramka-platnosci.git
cd rozproszona-bramka-platnosci
git checkout develop
```

### 3. `.env`

```bash
{
  for v in VISA_ORACLE_PASSWORD MASTERCARD_ORACLE_PASSWORD AMEX_ORACLE_PASSWORD BRAMKA_PASSWORD LINK_PASSWORD PANEL_ADMIN_PASSWORD PANEL_OPERATOR_PASSWORD; do echo "$v=P$(openssl rand -hex 12)"; done
  for v in KARTA_HMAC_KEY JWT_SECRET; do echo "$v=$(openssl rand -hex 32)"; done
} > .env
```

### 4. Start

| Co | Komenda | Adres |
|---|---|---|
| Węzły i migracje | `docker compose up -d` | Oracle `localhost:1521-1523` |
| Węzły, migracje i backend | `docker compose --profile aplikacja up -d --build` | API `http://localhost:8080/api/v1`, ISO 8583 `localhost:8583` |
| Mock API z kontraktu | `docker compose --profile mock up -d mock-api` | `http://localhost:4010` |
| Monitoring | `docker compose --profile aplikacja --profile monitoring up -d --build` | Grafana `http://localhost:3001`, Prometheus `http://localhost:9090` |
| Generator ruchu | `docker compose --profile ruch up -d --build generator-ruchu` | `docker logs -f generator-ruchu` |

Pierwszy start trwa kilka minut. Bazy są gotowe, gdy węzły mają status `healthy`, a kontenery `migracje-*` zakończyły się kodem 0.

### 5. Konta i dane demo

| Login | Rola | Hasło |
|---|---|---|
| `admin` | administrator | `PANEL_ADMIN_PASSWORD` z `.env` |
| `operator.visa`, `operator.mastercard`, `operator.amex` | operator sieci | `PANEL_OPERATOR_PASSWORD` z `.env` |

Backend przy pierwszym starcie wydaje karty demo z publicznych numerów testowych i kilka transakcji:

| Sieć | Numery kart |
|---|---|
| Visa | `4111111111111111`, `4012888888881881` |
| MasterCard | `5555555555554444`, `5105105105105100`, `2223003122003222` |
| American Express | `378282246310005`, `371449635398431` |

Klucz API sklepu generuje administrator: `POST /api/v1/sklepy/{id}/klucz-api`.

### 6. Połączenie z bazą

| Węzeł | Host | Service | User | Hasło |
|---|---|---|---|---|
| visa | `localhost:1521` | `FREEPDB1` | `bramka` | `BRAMKA_PASSWORD` z `.env` |
| mastercard | `localhost:1522` | `FREEPDB1` | `bramka` | `BRAMKA_PASSWORD` z `.env` |
| amex | `localhost:1523` | `FREEPDB1` | `bramka` | `BRAMKA_PASSWORD` z `.env` |

```bash
docker exec -it visa-db sqlplus bramka@//localhost/FREEPDB1
```

Reset baz: `docker compose down -v`

### 7. Monitoring i scenariusz obrony

Grafana pokazuje pulpit bramki bez logowania (tylko odczyt). Pełny ekran: `http://localhost:3001/d/bramka?kiosk`.

Generator ruchu zakłada przez API 3 sklepy i 21 kart i płaci nimi ok. 20 razy na sekundę (20% przez ISO 8583). Część kart ma niski limit, jest zablokowana albo w EUR, więc pojawiają się odmowy 51, 62, 57 i 14. Tempo ustawiają `RUCH_TPS` i `RUCH_UDZIAL_ISO` w `.env`. Zatrzymanie: `docker compose stop generator-ruchu`.

Scenariusz obrony, z katalogu projektu w Ubuntu:

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


## Backend bez Dockera

Wymaga Javy 21. Czyta sekrety z `../.env`, łączy się z węzłami na `localhost:1521-1523`.

```bash
cd backend
./mvnw spring-boot:run
```


## Testy

```bash
cd backend
./mvnw test
```

Testy integracyjne wymagają działających węzłów i backendu (np. profil `aplikacja`). Zatrzymują węzły i odłączają je od sieci, a po teście przywracają.

```bash
./mvnw -Pintegracja verify
```

Gdy Docker działa w WSL, a Maven na Windowsie: `./mvnw -Pintegracja verify -Ddocker.polecenie="wsl -d Ubuntu docker"`.
