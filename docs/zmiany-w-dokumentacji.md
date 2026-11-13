# Zmiany do naniesienia w dokumentacji projektu

Miejsca, w których implementacja odbiega od specyfikacji z 6 listopada 2026 albo ją doprecyzowuje. Numeracja sekcji jak w dokumencie „Rozproszone bazy danych - projekt”.

## 1. Analiza i wymagania

1. Oracle: Oracle AI Database 26ai Free, Release 23.26.3 (obraz `gvenzl/oracle-free:23.26.3`). Trzy węzły to trzy kontenery w jednej sieci Dockera.
2. FR2, kody odpowiedzi: poza `00, 14, 51, 54, 62, 91, 94, 96` są też kody ISO 8583 `03` (nieznany akceptant w kanale ISO), `12` (zwrot odrzuconej transakcji), `13` (kwota albo zwrot ponad kwotę), `25` (brak transakcji pierwotnej) i `57` (waluta inna niż waluta karty).
3. FR2, walidacja przed routingiem: zła długość numeru (poza 12–19), zła suma Luhna albo nieznany BIN dają HTTP 400 z kodem `14`, bez wywołania węzła. Poprawny numer, którego nie ma w bazie sieci, to odmowa z kodem `14`.
4. FR3: zwrot wskazuje transakcję pierwotną numerem RRN. Bramka kieruje go do węzła po cyfrze węzła w RRN (punkt 9).
5. FR4, FR5: klient i sklep są usuwane logicznie (status `USUNIETY`). Usunięcie sklepu unieważnia jego klucz API.
6. FR6: numer karty generuje bramka: 8-cyfrowy BIN wydawcy, numer rachunku i cyfra Luhna (16 cyfr dla Visa i MasterCard, 15 dla AmEx). Pełny numer jest zwracany raz, w odpowiedzi na wydanie karty. Nie trafia do bazy ani do logów, wszędzie indziej jest maska 6+4 (NFR6).
7. FR7, FR4, FR6 przy awarii węzła 1: blokada globalna, usunięcie klienta, wydanie i odblokowanie karty potrzebują kopii głównej, więc zwracają `503` z kodem `91`. Logowanie, autoryzacje, zwroty i odczyty działają na replikach.
8. NFR6, uwierzytelnianie:
   - panel: JWT HS256 ważny 30 min, hasła bcrypt (koszt 12), 5 nieudanych logowań na 5 min,
   - sklepy: klucz API w nagłówku `X-Api-Key`, w bazie tylko skrót SHA-256,
   - kanał ISO 8583 rozpoznaje sklep po polu DE42 i jest traktowany jak zaufane łącze agenta rozliczeniowego (port tylko na `127.0.0.1`).

## 2. Projekt bazy

9. RRN nadaje procedura węzła: `YDDD` + cyfra węzła + 7 cyfr z sekwencji `rrn_seq`. Bramka nie ma własnej bazy, licznik w pamięci powtarzałby numery po restarcie, a sekwencja na węźle 1 łamałaby NFR3. RRN ma ograniczenie `UNIQUE`.
10. Nowe kolumny:
    - `transakcja.id_zadania` (`NOT NULL`, `UNIQUE (id_sklepu, id_zadania)`) do idempotencji autoryzacji i zwrotów,
    - `sklep.hash_klucza_api` (`UNIQUE`),
    - `zakres_bin.dlugosc_pan`,
    - `siec_kartowa.nr_wezla` zamiast `wezel`,
    - `karta.waluta`, `karta.data_wydania`,
    - `klient.status`: `AKTYWNY`, `ZABLOKOWANY`, `USUNIETY`.
11. BIN ma 8 cyfr. Zakresy w `zakres_bin`: Visa 40000000–49999999, MasterCard 51000000–55999999 i 22210000–27209999, AmEx 34000000–34999999 i 37000000–37999999. Wyzwalacz `zakres_bin_rozlacznosc_trg` nie pozwala na nakładające się zakresy. Karty są wydawane z BIN-ów `bramka.wydawanie.biny`: Visa `45391000`, MasterCard `53999100`, AmEx `37971200`; bramka sprawdza, czy BIN leży w zakresie swojej sieci.
12. Tabela `zadanie_panelu` trzyma klucze idempotencji zapisów z panelu. Jest lokalna na każdym węźle, zapisywana w tej samej transakcji co zmiana, nie jest replikowana, wiersze starsze niż 7 dni są usuwane.
13. Kwoty w bazie mają typ `NUMBER(12,2)`, w API są liczbami całkowitymi w groszach.

## 3. Rozproszenie

14. Identyfikatory: `IDENTITY START WITH <nr węzła> INCREMENT BY 10` zamiast kroku 3. Przy kroku 3 czwarta sieć wymagałaby zmiany istniejących tabel (wbrew NFR7), krok 10 starcza na 10 sieci. Bramka rozpoznaje węzeł karty i transakcji po `id mod 10`.
15. Warunek fragmentu: `CHECK (id_sieci = <nr węzła>)` na tabeli `karta`.
16. Replikacja: logi MV `WITH PRIMARY KEY` na pięciu tabelach węzła 1, na węzłach 2 i 3 widoki `REFRESH FAST ON DEMAND` w grupie `GRP_DANE_WSPOLNE`. Grupę co 30 s odświeża zadanie `DBMS_SCHEDULER` `ODSWIEZ_DANE_WSPOLNE`, a nie interwał z `DBMS_REFRESH.MAKE`: stary mechanizm zadań wyłącza odświeżanie po 16 błędach z rzędu (np. przy dłuższej awarii sieci), a zadanie harmonogramu ponawia bez końca i zapisuje nieudane próby (FR11).
17. Łącza bazodanowe łączą się jako osobny użytkownik `wezel_link`: na węźle 1 ma `SELECT` na tabelach wspólnych i ich logach MV, na węzłach sieci `SELECT` na `karta` i `transakcja` oraz `UPDATE (status)` na `karta`. Łącza nazywają się jak kody sieci (`VISA`, `MC`, `AMEX`), synonimy: `klient_master`, `karta_mc`, `karta_amex`, `transakcja_mc`, `transakcja_amex`.
18. Klucze obce do klientów i sklepów są prawdziwymi `FOREIGN KEY` na węźle 1, na węzłach 2 i 3 sprawdzają je pakiety PL/SQL. Brak klienta albo sklepu w lokalnej replice daje HTTP 409 `REPLIKACJA_W_TOKU` z `Retry-After`.
19. Widoki globalne `v_transakcja_globalna` i `v_karta_globalna` są na węźle 1 (`UNION ALL` przez synonimy). Raporty FR9 i FR10 w aplikacji pytają węzły równolegle, żeby przy awarii jednego zwrócić wynik częściowy; widok globalny wymaga wszystkich węzłów.
20. Idempotencja autoryzacji działa w obrębie sieci: `(id_sklepu, id_zadania)` jest sprawdzane na węźle sieci karty. Ten sam `idZadania` dla kart dwóch różnych sieci nie jest wykrywany, bo wymagałoby to pytania wszystkich węzłów przy każdej autoryzacji (wbrew NFR1 i NFR3).
21. Zatrzymany kontener węzła daje na łączu błąd ORA-12262 bez nazwy łącza. Pakiety `pkg_klienci` i `pkg_karty` łapią błędy łączności przy każdej operacji zdalnej i zgłaszają `ORA-20091: Siec niedostepna: <SIEC>`, a bramka zamienia to na HTTP 503 z kodem `91` i nazwą sieci.
22. Po restarcie węzła inne węzły mają martwe sesje łączy. Bramka przy takim błędzie wymienia połączenia w puli i ponawia operację panelu raz. Operacja nie była zatwierdzona, a klucz idempotencji chroni przed podwójnym wykonaniem.
23. Skalowalność (NFR7): nowa sieć to nowy węzeł z migracjami `repliki` i `fragmenty` (parametr `nr_wezla`) i dwa wiersze danych (`siec_kartowa`, `zakres_bin`). Istniejące tabele się nie zmieniają. Bramka dostaje adres węzła w konfiguracji; `pkg_klienci` i widoki globalne trzeba rozszerzyć o łącze do nowego węzła (kod, nie tabele).

## 6. Testy

24. Testy są w `backend/src/test/java/.../integracja` (`./mvnw -Pintegracja verify`). Każda odpowiedź API jest sprawdzana względem `api/openapi.yaml`.
25. Test 9b wymusza awarię w trakcie 2PC przez `COMMIT COMMENT 'ORA-2PC-CRASH-TEST-4'`. Potrzebuje uprawnienia `FORCE ANY TRANSACTION`, które test nadaje na czas symulacji i odbiera; aplikacja go nie ma.

## Technologia

26. Backend: Spring Boot 4.1, Java 21, Spring JDBC i procedury PL/SQL (bez JPA), sterownik `ojdbc17`. Migracje Flyway 13 uruchamia `docker compose`: Visa, potem MasterCard i AmEx, na końcu połączenia Visy z węzłami sieci.
27. jPOS 2.1.10 zamiast 3.0.0, bo 3.0.0 wymaga Javy 22. Komunikaty ISO 8583 (1987, ASCII): `0100/0110` autoryzacja, `0400/0410` wycofanie; `id_zadania` = DE42 + DE11 + DE7; DE37 to RRN, DE39 kod odpowiedzi, DE38 kod autoryzacji. jPOS ma licencję AGPL-3.0, co trzeba odnotować przy publicznym repozytorium.
28. Specyfikacje Visa, MasterCard i American Express są dostępne tylko dla członków sieci, więc projekt opiera się na publicznych standardach: ISO 8583 (komunikaty, pole 39), ISO/IEC 7812 (BIN, Luhn), PCI DSS (maska 6+4, brak pełnego numeru i CVV). Zakresy BIN są przybliżone.

## Monitoring i demonstracja

29. Bramka wystawia `/actuator/prometheus` (bez logowania, port tylko na `127.0.0.1`). Metryki nie zawierają numerów kart ani danych klientów:
    - `bramka_platnosci_total{siec, typ, kod, powtorzona}` i histogram `bramka_platnosci_czas_seconds`,
    - `bramka_wezel_dostepny`, `bramka_replikacja_opoznienie_sekundy`, `bramka_transakcje_2pc_wiszace` (według sieci) i `bramka_log_mv_wiersze` (według tabeli), co 5 s z danych FR11,
    - `bramka_iso8583_komunikaty_total{mti, kod}`.
30. Grafana (profil `monitoring`) ładuje pulpit z `monitoring/grafana/dashboards/bramka.json`. Dostęp jest anonimowy i tylko do odczytu, bez konta administratora. Linie przerywane na wykresach to limity NFR1 (500 ms) i NFR5 (60 s).
31. Generator ruchu (profil `ruch`) to klasa `pl.janicki.bramka.demonstracja.GeneratorRuchu` uruchamiana z obrazu backendu. Sklepy i karty zakłada przez API panelu, płaci przez REST i ISO 8583. Pełne numery kart trzyma tylko w pamięci.
32. `demonstracja/scenariusz-obrony.sh` pokazuje testy z sekcji 6 na działającym systemie pod ruchem. Czwarta sieć dostaje adres w konfiguracji bramki z `demonstracja/czwarta-siec.yml` (lista węzłów w zmiennych `BRAMKA_WEZLY_n_*`), więc bramka się restartuje.

## Poza zakresem specyfikacji

Rzeczy, które przybliżyłyby system do prawdziwych wydawców, ale zmieniłyby FR2, scenariusz 1 i ERD, więc nie zostały zrobione:

- data ważności i CVV2/CID w żądaniu autoryzacji, sprawdzane bez zapisu (emulacja HSM), kod `N7` przy złym CVV,
- blokada karty po kilku błędnych CVV z rzędu,
- 6-znakowy kod autoryzacji (DE38) zapisywany przy transakcji i zwracany sklepowi,
- dane nowej karty do personalizacji w zaszyfrowanym pakiecie (np. JWE dla drukarni kart) zamiast w odpowiedzi API.
