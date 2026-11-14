#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

API=http://127.0.0.1:8080/api/v1
GRAFANA=http://localhost:3001/d/bramka
SIEC_DOCKERA=bramka-platnosci_siec-wezlow
TMP=$(mktemp -d)
AUTO=false
KROKI=()
WYNIKI=()
VISA_ZATRZYMANA=false
MC_ODLACZONA=false
CZWARTA_SIEC=false

for argument in "$@"; do
  case $argument in
    --auto) AUTO=true ;;
    [1-5]) KROKI+=("$argument") ;;
    -h|--help)
      echo "Użycie: $0 [--auto] [numery kroków 1-5]"
      echo "  bez numerów: wszystkie kroki; --auto: bez zatrzymywania się między krokami"
      exit 0 ;;
    *) echo "Nieznany argument: $argument" >&2; exit 2 ;;
  esac
done
[[ ${#KROKI[@]} -eq 0 ]] && KROKI=(1 2 3 4 5)

if [[ ! -f .env ]]; then
  echo "Brak pliku .env w katalogu projektu" >&2
  exit 1
fi
set -a
. ./.env
set +a

GRUBO=$'\e[1m'; ZIELONY=$'\e[32m'; CZERWONY=$'\e[31m'; ZOLTY=$'\e[33m'; SZARY=$'\e[90m'; RESET=$'\e[0m'

naglowek() { printf '\n%s━━━ %s ━━━%s\n' "$GRUBO" "$1" "$RESET"; }
opis() { printf '%s%s%s\n' "$SZARY" "$1" "$RESET"; }
ok() { printf '%s✔ %s%s\n' "$ZIELONY" "$1" "$RESET"; }
uwaga() { printf '%s! %s%s\n' "$ZOLTY" "$1" "$RESET"; }
blad() { printf '%s✘ %s%s\n' "$CZERWONY" "$1" "$RESET" >&2; }
wynik() { WYNIKI+=("$1"); ok "$1"; }

pauza() {
  if $AUTO; then
    sleep 2
  else
    read -r -p "$(printf '%s↵ %s%s' "$GRUBO" "$1" "$RESET")" _ </dev/tty
  fi
}

uuid() { python3 -c 'import uuid; print(uuid.uuid4())'; }
teraz() { date +%s; }

pole() {
  python3 -c '
import json, sys
dane = json.load(open(sys.argv[1]))
for klucz in sys.argv[2].split("."):
    dane = dane[int(klucz)] if isinstance(dane, list) else dane.get(klucz)
print("" if dane is None else dane)' "$TMP/odpowiedz.json" "$1"
}

zadanie() {
  local metoda=$1 sciezka=$2 uwierzytelnienie=$3 tresc=${4:-}
  local argumenty=(-s -o "$TMP/odpowiedz.json" -w '%{http_code}' -X "$metoda" "$API$sciezka" -H "@$uwierzytelnienie"
    -H "Idempotency-Key: $(uuid)" --max-time 30)
  if [[ -n $tresc ]]; then
    argumenty+=(-H 'Content-Type: application/json' --data-binary "$tresc")
  fi
  STATUS=$(curl "${argumenty[@]}" || echo 000)
}

zaloguj() {
  local login=$1 haslo=$2 plik=$TMP/naglowek-$1
  local status
  status=$(printf '{"login":"%s","haslo":"%s"}' "$login" "$haslo" | curl -s -o "$TMP/odpowiedz.json" -w '%{http_code}' \
    -X POST "$API/auth/logowanie" -H 'Content-Type: application/json' --data-binary @- --max-time 15 || echo 000)
  [[ $status == 200 ]] || return 1
  printf 'Authorization: Bearer %s\n' "$(pole token)" > "$plik"
  echo "$plik"
}

naglowek_klucza() {
  printf 'X-Api-Key: %s\n' "$1" > "$TMP/klucz-api"
  echo "$TMP/klucz-api"
}

sql() {
  local kontener=$1 zapytanie=$2
  printf 'WHENEVER SQLERROR EXIT FAILURE ROLLBACK\nCONNECT bramka/"%s"@//localhost:1521/FREEPDB1\nSET HEADING OFF FEEDBACK OFF PAGESIZE 0 LINESIZE 200 VERIFY OFF TRIMSPOOL ON\n%s\nEXIT\n' \
    "$BRAMKA_PASSWORD" "$zapytanie" | docker exec -i "$kontener" sqlplus -s -L /nolog | sed -e '/^[[:space:]]*$/d' -e 's/^[[:space:]]*//'
}

czekaj_na_zdrowie() {
  local kontener=$1 limit=${2:-300} start
  start=$(teraz)
  until [[ $(docker inspect -f '{{.State.Health.Status}}' "$kontener" 2>/dev/null) == healthy ]]; do
    (( $(teraz) - start > limit )) && return 1
    sleep 2
  done
}

czekaj_na_bramke() {
  local start
  start=$(teraz)
  until ADMIN=$(zaloguj admin "$PANEL_ADMIN_PASSWORD"); do
    (( $(teraz) - start > 180 )) && { blad "Bramka nie odpowiada"; return 1; }
    sleep 2
  done
}

pokaz_ruch() {
  local sekundy=$1
  opis "Generator ruchu (co 5 s):"
  timeout "$sekundy" docker logs -f --since 1s generator-ruchu 2>&1 | sed 's/^/    /' || true
}

wydaj_karte() {
  local siec=$1 klient=$2 limit=$3 operator start
  operator=$(zaloguj "$(login_operatora "$siec")" "$PANEL_OPERATOR_PASSWORD")
  start=$(teraz)
  while true; do
    zadanie POST /karty "$operator" "{\"idKlienta\":$klient,\"limitKredytowy\":$limit}"
    [[ $STATUS == 409 && $(pole kod) == REPLIKACJA_W_TOKU && $(( $(teraz) - start )) -lt 120 ]] || break
    sleep 3
  done
  [[ $STATUS == 201 ]] || { blad "Wydanie karty $siec: HTTP $STATUS $(cat "$TMP/odpowiedz.json")"; return 1; }
  echo "$(pole karta.id) $(pole numerKarty)"
}

login_operatora() {
  case $1 in
    VISA) echo operator.visa ;;
    MC) echo operator.mastercard ;;
    AMEX) echo operator.amex ;;
    DISC) echo operator.disc ;;
  esac
}

nowy_klient() {
  zadanie POST /klienci "$ADMIN" "{\"imie\":\"$1\",\"nazwisko\":\"$2\",\"email\":\"$3\",\"kraj\":\"PL\"}"
  [[ $STATUS == 201 ]] || { blad "Nowy klient: HTTP $STATUS"; return 1; }
  pole id
}

nip() {
  python3 -c '
import random
wagi = [6, 5, 7, 2, 3, 4, 5, 6, 7]
while True:
    cyfry = [random.randint(1, 9)] + [random.randint(0, 9) for _ in range(8)]
    suma = sum(w * c for w, c in zip(wagi, cyfry)) % 11
    if suma != 10:
        print("".join(map(str, cyfry)) + str(suma))
        break'
}

autoryzuj() {
  local numer=$1 kwota=$2
  zadanie POST /platnosci/autoryzacje "$(naglowek_klucza "$KLUCZ_SKLEPU")" \
    "{\"idZadania\":\"obrona-$(uuid)\",\"numerKarty\":\"$numer\",\"kwota\":$kwota,\"waluta\":\"PLN\"}"
}

przywroc() {
  if $VISA_ZATRZYMANA; then
    uwaga "Uruchamiam z powrotem węzeł Visa"
    docker start visa-db >/dev/null || true
  fi
  if $MC_ODLACZONA; then
    uwaga "Podłączam z powrotem węzeł MasterCard"
    docker network connect "$SIEC_DOCKERA" mastercard-db 2>/dev/null || true
  fi
  if $CZWARTA_SIEC; then
    usun_czwarta_siec || true
  fi
  rm -rf "$TMP"
}
trap przywroc EXIT

przygotowanie() {
  naglowek "Przygotowanie"
  for kontener in visa-db mastercard-db amex-db; do
    czekaj_na_zdrowie "$kontener" 600 || { blad "Węzeł $kontener nie jest gotowy (docker compose up -d)"; exit 1; }
  done
  ok "Trzy węzły Oracle działają"
  if ! docker ps --format '{{.Names}}' | grep -qx backend; then
    opis "Uruchamiam backend"
    docker compose --profile aplikacja up -d --build backend >/dev/null 2>&1
  fi
  czekaj_na_bramke
  ok "Bramka odpowiada na $API"
  docker compose --profile monitoring up -d prometheus grafana >/dev/null 2>&1
  ok "Grafana: $GRAFANA (kiosk: $GRAFANA?kiosk)"
  if ! docker ps --format '{{.Names}}' | grep -qx generator-ruchu; then
    opis "Uruchamiam generator ruchu (sklepy i karty demonstracyjne powstają przez API, ok. 30-60 s)"
    docker compose --profile ruch up -d --build generator-ruchu >/dev/null 2>&1
  fi
  local start
  start=$(teraz)
  until docker logs generator-ruchu 2>&1 | grep -q 'Start ruchu'; do
    (( $(teraz) - start > 240 )) && { blad "Generator nie wystartował: docker logs generator-ruchu"; exit 1; }
    sleep 3
  done
  ok "Generator ruchu działa ($(docker logs generator-ruchu 2>&1 | grep -m1 'Generator ruchu' | cut -d, -f1 | sed 's/Generator ruchu: //'))"

  zadanie POST /sklepy "$ADMIN" "{\"nazwa\":\"Sklep obrony $(date +%H%M%S)\",\"nip\":\"$(nip)\",\"kategoriaMcc\":\"5999\",\"emailKontaktowy\":\"obrona-$(uuid | cut -c1-8)@example.com\"}"
  [[ $STATUS == 201 ]] || { blad "Nowy sklep: HTTP $STATUS"; exit 1; }
  ID_SKLEPU=$(pole id)
  zadanie POST "/sklepy/$ID_SKLEPU/klucz-api" "$ADMIN"
  KLUCZ_SKLEPU=$(pole kluczApi)
  KLIENT_TESTOWY=$(nowy_klient "Ewa" "Testowa" "obrona-$(uuid | cut -c1-8)@example.com")
  ok "Sklep scenariusza: id $ID_SKLEPU, klient testowy: id $KLIENT_TESTOWY (replikują się do MC i AmEx w tle)"
}

krok_ruch() {
  naglowek "1. Normalna praca: routing po BIN do trzech węzłów"
  opis "Generator płaci kartami trzech sieci przez REST i ISO 8583 (jPOS). Bramka rozpoznaje sieć po BIN"
  opis "i wywołuje procedurę autoryzacji na węźle tej sieci. Odmowy 51/62/57/14 są celowe (limit, blokada, waluta, zły numer)."
  pokaz_ruch 11
  zadanie GET /wezly/stan "$ADMIN"
  python3 -c '
import json, sys
for w in json.load(open(sys.argv[1]))["wezly"]:
    rep = w.get("replikacja") or {}
    opoz = rep.get("opoznienieSekund")
    print("    %-5s %-8s %-11s %s ms%s" % (w["siec"], w["rola"], "dostępny" if w["dostepny"] else "NIEDOSTĘPNY",
          w.get("czasOdpowiedziMs"), "" if opoz is None else ", replika sprzed %s s" % opoz))' "$TMP/odpowiedz.json"
  wynik "Ruch na trzech węzłach, stan węzłów z FR11"
  pauza "Krok 2: awaria węzła Visa"
}

krok_awaria_visa() {
  naglowek "2. Awaria węzła 1 (Visa, kopia główna) - NFR3"
  opis "Zatrzymuję kontener visa-db. Karty Visa dostają 91 (sieć niedostępna), MasterCard i AmEx działają dalej,"
  opis "bo dane wspólne (klienci, sklepy, BIN-y, użytkownicy) mają lokalnie w replikach."
  local start
  start=$(teraz)
  docker stop visa-db >/dev/null
  VISA_ZATRZYMANA=true
  ok "visa-db zatrzymany"
  pokaz_ruch 16
  if ADMIN=$(zaloguj admin "$PANEL_ADMIN_PASSWORD"); then
    ok "Logowanie do panelu działa bez węzła 1 (tabela uzytkownik z repliki)"
  else
    blad "Logowanie bez węzła 1 nie działa"
  fi
  zadanie POST "/klienci/$KLIENT_TESTOWY/blokada-globalna" "$ADMIN"
  ok "Blokada globalna (FR7) bez węzła 1: HTTP $STATUS, kod $(pole kod), sieć $(pole szczegoly.siec)"
  pauza "Uruchom ponownie węzeł Visa"
  docker start visa-db >/dev/null
  opis "Oracle startuje (ok. 30-60 s). Repliki MC i AmEx same wznowią odświeżanie."
  czekaj_na_zdrowie visa-db 300
  VISA_ZATRZYMANA=false
  local powrot=$(( $(teraz) - start ))
  ok "visa-db znowu działa"
  pokaz_ruch 11
  wynik "Awaria Visy: MC i AmEx autoryzowały cały czas, Visa wróciła po ${powrot} s od zatrzymania"
  pauza "Krok 3: odcięcie sieci węzła MasterCard"
}

krok_odciecie_mc() {
  naglowek "3. Odcięcie węzła MasterCard od sieci - spójność replik (NFR5)"
  opis "Odłączam mastercard-db od sieci Dockera. W tym czasie zmieniam dane wspólne na węźle 1."
  opis "Zmiany czekają w logu MLOG\$ na Visie; po podłączeniu REFRESH FAST dociąga tylko różnicę."
  docker network disconnect "$SIEC_DOCKERA" mastercard-db
  MC_ODLACZONA=true
  local odlaczenie
  odlaczenie=$(teraz)
  ok "mastercard-db odłączony"
  local znacznik i
  znacznik=$(uuid | cut -c1-8)
  for i in 1 2 3 4 5; do
    nowy_klient "Klient" "Odciecia$i" "odciecie-$znacznik-$i@example.com" >/dev/null
  done
  ok "Dodano 5 klientów na węźle 1 (kopia główna)"
  opis "Klienci z tej serii: Visa $(sql visa-db "SELECT COUNT(*) FROM klient WHERE email LIKE 'odciecie-$znacznik-%';"), \
MasterCard $(sql mastercard-db "SELECT COUNT(*) FROM klient WHERE email LIKE 'odciecie-$znacznik-%';"), \
AmEx po odświeżeniu do 30 s"
  opis "Log zmian na Visie: $(sql visa-db 'SELECT COUNT(*) FROM mlog$_klient;') wierszy w MLOG\$_KLIENT"
  pokaz_ruch 21
  local czekam=$(( 50 - ($(teraz) - odlaczenie) ))
  (( czekam > 0 )) && { opis "Odcięcie trwa jeszcze $czekam s (w Grafanie opóźnienie MC przekracza 60 s)"; sleep "$czekam"; }
  pauza "Podłącz z powrotem węzeł MasterCard"
  docker network connect "$SIEC_DOCKERA" mastercard-db
  MC_ODLACZONA=false
  local podlaczenie
  podlaczenie=$(teraz)
  ok "mastercard-db podłączony po $(( podlaczenie - odlaczenie )) s"
  until [[ $(sql mastercard-db "SELECT COUNT(*) FROM klient WHERE email LIKE 'odciecie-$znacznik-%';") == 5 ]]; do
    (( $(teraz) - podlaczenie > 120 )) && { blad "Replika MC nie nadrobiła zmian w 120 s"; return 1; }
    sleep 2
  done
  local nadrobienie=$(( $(teraz) - podlaczenie ))
  ok "Replika MasterCard ma wszystkich 5 klientów po ${nadrobienie} s od podłączenia"
  opis "Log zmian na Visie po odświeżeniu: $(sql visa-db 'SELECT COUNT(*) FROM mlog$_klient;') wierszy"
  wynik "Odcięcie MC na $(( podlaczenie - odlaczenie )) s: replika nadrobiła zmiany w ${nadrobienie} s (limit NFR5: 60 s)"
  pauza "Krok 4: blokada klienta we wszystkich sieciach (2PC)"
}

krok_blokada_2pc() {
  naglowek "4. Blokada klienta we wszystkich sieciach - transakcja rozproszona 2PC (FR7, NFR4)"
  opis "Klient ma karty w trzech sieciach. Procedura na węźle 1 blokuje klienta i jego karty na MC i AmEx"
  opis "przez łącza bazodanowe w jednej transakcji; Oracle zatwierdza ją dwufazowo (prepare/commit)."
  local klient
  klient=$(nowy_klient "Jan" "Rozproszony" "2pc-$(uuid | cut -c1-8)@example.com")
  opis "Klient $klient, wydaję karty (MC i AmEx czekają, aż klient trafi do replik)"
  local visa mc amex
  visa=$(wydaj_karte VISA "$klient" 100000)
  mc=$(wydaj_karte MC "$klient" 100000)
  amex=$(wydaj_karte AMEX "$klient" 100000)
  local numer_mc=${mc#* }
  autoryzuj "$numer_mc" 2500
  ok "Karty: Visa ${visa%% *}, MC ${mc%% *}, AmEx ${amex%% *}; płatność kartą MC przed blokadą: kod $(pole kod)"
  pauza "Zablokuj klienta globalnie"
  zadanie POST "/klienci/$klient/blokada-globalna" "$ADMIN"
  [[ $STATUS == 200 ]] || { blad "Blokada: HTTP $STATUS $(cat "$TMP/odpowiedz.json")"; return 1; }
  ok "Blokada globalna: zablokowane karty VISA=$(pole zablokowaneKarty.VISA) MC=$(pole zablokowaneKarty.MC) AMEX=$(pole zablokowaneKarty.AMEX)"
  opis "Stan w bazach:"
  printf '    Visa (klient):  %s\n' "$(sql visa-db "SELECT status FROM klient WHERE id_klienta = $klient;")"
  printf '    Visa (karta):   %s\n' "$(sql visa-db "SELECT status FROM karta WHERE id_karty = ${visa%% *};")"
  printf '    MC (karta):     %s\n' "$(sql mastercard-db "SELECT status FROM karta WHERE id_karty = ${mc%% *};")"
  printf '    AmEx (karta):   %s\n' "$(sql amex-db "SELECT status FROM karta WHERE id_karty = ${amex%% *};")"
  printf '    DBA_2PC_PENDING: %s\n' "$(sql visa-db 'SELECT COUNT(*) FROM sys.dba_2pc_pending;')"
  autoryzuj "$numer_mc" 2500
  ok "Płatność kartą MC po blokadzie: kod $(pole kod) ($(pole komunikat))"
  wynik "Blokada 2PC: klient i karty zablokowane atomowo na trzech węzłach, brak transakcji w toku"
  pauza "Krok 5: czwarta sieć na żywo"
}

usun_czwarta_siec() {
  opis "Usuwam czwarty węzeł i przywracam konfigurację trzech sieci"
  docker compose --profile aplikacja up -d --no-deps backend >/dev/null 2>&1 || true
  if docker ps --format '{{.Names}}' | grep -qx discover-db; then
    sql discover-db "BEGIN FOR w IN (SELECT mview_name FROM user_mviews) LOOP EXECUTE IMMEDIATE 'DROP MATERIALIZED VIEW ' || w.mview_name; END LOOP; END;
/" >/dev/null || true
  fi
  sql visa-db "DELETE FROM uzytkownik WHERE id_sieci = 4;
DELETE FROM zakres_bin WHERE id_sieci = 4;
DELETE FROM siec_kartowa WHERE id_sieci = 4;
COMMIT;" >/dev/null
  docker rm -f -v discover-db >/dev/null 2>&1 || true
  CZWARTA_SIEC=false
  czekaj_na_bramke
  ok "Przywrócono trzy sieci"
}

krok_czwarta_siec() {
  naglowek "5. Czwarta sieć (Discover) na żywo - skalowalność bez zmian w tabelach (NFR7)"
  opis "Nowy kontener Oracle, te same migracje co MC i AmEx (repliki + fragmenty, nr_wezla=4),"
  opis "dwa wiersze danych na węźle 1 i adres węzła w konfiguracji bramki. Istniejące tabele bez zmian."
  local struktura_przed
  struktura_przed=$(sql mastercard-db "SELECT COUNT(*) || ':' || SUM(ORA_HASH(table_name || column_name || data_type || data_length)) FROM user_tab_columns;")
  CZWARTA_SIEC=true
  local start
  start=$(teraz)
  docker rm -f -v discover-db >/dev/null 2>&1 || true
  ORACLE_PASSWORD="P$(uuid | tr -d -)" docker run -d --name discover-db --network "$SIEC_DOCKERA" -p 127.0.0.1:1524:1521 \
    --security-opt no-new-privileges:true \
    --health-cmd healthcheck.sh --health-interval 5s --health-retries 60 \
    -e ORACLE_PASSWORD -e BRAMKA_PASSWORD -e LINK_PASSWORD \
    -v "$PWD/database/init:/container-entrypoint-initdb.d:ro" \
    gvenzl/oracle-free:23.26.3 >/dev/null
  opis "Startuje discover-db (ok. 1 min)"
  czekaj_na_zdrowie discover-db 600 || { blad "discover-db nie wystartował"; return 1; }
  ok "Węzeł 4 działa po $(( $(teraz) - start )) s, uruchamiam migracje"
  FLYWAY_PASSWORD=$BRAMKA_PASSWORD FLYWAY_PLACEHOLDERS_LINK_HASLO=$LINK_PASSWORD \
  docker run --rm --network "$SIEC_DOCKERA" -v "$PWD/database:/flyway/projekt:ro" \
    -e FLYWAY_URL=jdbc:oracle:thin:@//discover-db:1521/FREEPDB1 -e FLYWAY_USER=bramka -e FLYWAY_PASSWORD \
    -e FLYWAY_CONNECT_RETRIES=20 \
    -e FLYWAY_LOCATIONS=filesystem:/flyway/projekt/migrations/repliki,filesystem:/flyway/projekt/migrations/fragmenty \
    -e FLYWAY_PLACEHOLDERS_NR_WEZLA=4 -e FLYWAY_PLACEHOLDERS_LINK_HASLO \
    -e FLYWAY_PLACEHOLDERS_ADRES_VISA=//visa-db:1521/FREEPDB1 -e FLYWAY_PLACEHOLDERS_ADRES_MC=//mastercard-db:1521/FREEPDB1 \
    -e FLYWAY_PLACEHOLDERS_ADRES_AMEX=//amex-db:1521/FREEPDB1 \
    flyway/flyway:13.12.2 -q migrate
  ok "Migracje węzła 4 zakończone"
  sql visa-db "INSERT INTO siec_kartowa (id_sieci, kod, nazwa, nr_wezla) VALUES (4, 'DISC', 'Discover', 4);
INSERT INTO zakres_bin (id_sieci, bin_od, bin_do, dlugosc_pan) VALUES (4, 60110000, 60119999, 16);
COMMIT;" >/dev/null
  local dane
  dane=$(teraz)
  ok "Węzeł 1: dodano sieć DISC i zakres BIN 6011xxxx"
  until [[ $(sql discover-db "SELECT COUNT(*) FROM zakres_bin WHERE id_sieci = 4;") == 1 ]]; do
    (( $(teraz) - dane > 120 )) && { blad "Replika węzła 4 nie dostała danych"; return 1; }
    sleep 2
  done
  ok "Replika węzła 4 ma nową sieć po $(( $(teraz) - dane )) s, klientów: $(sql discover-db 'SELECT COUNT(*) FROM klient;')"
  opis "Bramka dostaje adres węzła 4 w konfiguracji (demonstracja/czwarta-siec.yml) i restartuje się (ok. 10 s)"
  docker compose -f docker-compose.yml -f demonstracja/czwarta-siec.yml --profile aplikacja up -d --no-deps backend >/dev/null 2>&1
  sleep 5
  czekaj_na_bramke
  local operator start_op karta
  start_op=$(teraz)
  until operator=$(zaloguj operator.disc "$PANEL_OPERATOR_PASSWORD"); do
    (( $(teraz) - start_op > 60 )) && { blad "Brak konta operator.disc"; return 1; }
    sleep 2
  done
  ok "Bramka zna 4 węzły, operator.disc może się zalogować"
  karta=$(wydaj_karte DISC "$KLIENT_TESTOWY" 5000000)
  local numer=${karta#* }
  ok "Wydano kartę Discover ${numer:0:6}******${numer: -4} (id ${karta%% *}, końcówka id = nr węzła)"
  local zatwierdzone=0 i
  for i in $(seq 1 30); do
    autoryzuj "$numer" $(( (RANDOM % 9000) + 500 ))
    [[ $(pole kod) == 00 ]] && zatwierdzone=$((zatwierdzone + 1))
    [[ $i == 1 ]] && opis "Pierwsza płatność kartą Discover: kod $(pole kod), RRN $(pole rrn) (5. cyfra RRN = węzeł 4)"
    sleep 0.5
  done
  ok "Płatności kartą Discover: $zatwierdzone/30 zatwierdzonych (w Grafanie pojawia się linia DISC)"
  local struktura_po
  struktura_po=$(sql mastercard-db "SELECT COUNT(*) || ':' || SUM(ORA_HASH(table_name || column_name || data_type || data_length)) FROM user_tab_columns;")
  if [[ $struktura_przed == "$struktura_po" ]]; then
    ok "Struktura tabel węzła MasterCard identyczna przed i po dodaniu sieci"
  else
    blad "Struktura tabel MasterCard zmieniła się"
  fi
  wynik "Czwarta sieć: węzeł, migracje i dane gotowe w $(( $(teraz) - start )) s, $zatwierdzone/30 autoryzacji Discover"
  if $AUTO; then
    sleep 20
    usun_czwarta_siec
  else
    local odpowiedz
    read -r -p "Usunąć czwarty węzeł i wrócić do trzech sieci? [T/n] " odpowiedz </dev/tty
    [[ ${odpowiedz:-t} =~ ^[Tt]$ ]] && usun_czwarta_siec
  fi
}

przygotowanie
opis "Otwórz Grafanę: $GRAFANA"
pauza "Start scenariusza"
for krok in "${KROKI[@]}"; do
  case $krok in
    1) krok_ruch ;;
    2) krok_awaria_visa ;;
    3) krok_odciecie_mc ;;
    4) krok_blokada_2pc ;;
    5) krok_czwarta_siec ;;
  esac
done

naglowek "Podsumowanie"
for linia in "${WYNIKI[@]}"; do
  ok "$linia"
done
opis "Generator nadal działa. Zatrzymanie: docker compose stop generator-ruchu"
