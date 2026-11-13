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
api/                    kontrakt REST (OpenAPI) między backendem a frontendem
backend/                Spring Boot, Java 21
database/
  init/                 tworzenie użytkownika przy pierwszym starcie węzła
  migrations/<węzeł>/   schemat bazy per węzeł (Flyway)
  seed/                 dane testowe
  queries/              przykładowe zapytania rozproszone
docs/                   raport i diagramy
frontend/               UI
tests/                  testy wymagań niefunkcjonalnych (czas, obciążenie, spójność, awarie, skalowalność)
docker-compose.yml      trzy węzły Oracle
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
for v in VISA_ORACLE_PASSWORD MASTERCARD_ORACLE_PASSWORD AMEX_ORACLE_PASSWORD BRAMKA_PASSWORD; do echo "$v=P$(openssl rand -hex 12)"; done > .env
```

### 4. Start

```bash
docker compose up -d
docker compose ps
```

Gotowe, gdy wszystkie węzły mają status `healthy`.

### 5. Połączenie

| Węzeł | Host | Service | User | Hasło |
|---|---|---|---|---|
| visa | `localhost:1521` | `FREEPDB1` | `bramka` | `BRAMKA_PASSWORD` z `.env` |
| mastercard | `localhost:1522` | `FREEPDB1` | `bramka` | `BRAMKA_PASSWORD` z `.env` |
| amex | `localhost:1523` | `FREEPDB1` | `bramka` | `BRAMKA_PASSWORD` z `.env` |

```bash
docker exec -it visa-db sqlplus bramka@//localhost/FREEPDB1
```

Reset baz: `docker compose down -v`
