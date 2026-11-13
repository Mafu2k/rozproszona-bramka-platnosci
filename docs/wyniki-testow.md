# Wyniki testów (sekcja 6)

Środowisko: Windows 11, Docker Engine w WSL2 (Ubuntu 26.04), trzy węzły Oracle AI Database 26ai Free 23.26.3, backend Spring Boot 4.1.2 (Java 21) w kontenerze. Uruchomienie z katalogu `backend`:

```bash
./mvnw -Pintegracja verify -Ddocker.polecenie="wsl -d Ubuntu docker"
```

Wynik z 13 listopada 2026: 139 testów jednostkowych (z powtórzeniami testów losowych) i 26 integracyjnych, wszystkie zaliczone. Każda odpowiedź API w testach integracyjnych była sprawdzana względem `api/openapi.yaml`.

| Test | Wymaganie | Klasa | Wynik |
|---|---|---|---|
| 6.1 czas odpowiedzi | NFR1: autoryzacja < 500 ms przy 10 000 transakcji w węźle, raport globalny < 3 s | `WydajnoscIT` | 14 816 transakcji w węźle MC: mediana 48 ms, p95 52 ms, maks. 53 ms; statystyki sieci 14 ms, obroty sklepu 11 ms |
| 6.2 obciążenie | NFR2: 100 równoległych autoryzacji bez błędów, średnio < 1 s | `ObciazenieIT` | średnio 160 ms, p95 259 ms, maks. 264 ms; 60 żądań na kartę z limitem na 50: dokładnie 50 zatwierdzonych i 10 odmów (51), saldo nigdy ujemne |
| 6.3 spójność | NFR5: zmiany danych wspólnych na replikach ≤ 60 s | `ReplikacjaIT` | zmiana klienta widoczna na MC po 28,5 s, na AmEx po 29,6 s |
| 6.3 spójność | Utrata sieci w trakcie odświeżania | `OdlaczenieSieciIT` | MC odłączony na ok. 40 s, w tym czasie 3 nowych klientów i 2 zmiany na węźle 1; replika zgodna 22 s po podłączeniu, bez braków i duplikatów; FR11 pokazał węzeł jako niedostępny, potem aktualny |
| 6.4 awaria | NFR3: zatrzymany węzeł 1 | `AwariaWezlaGlownegoIT` | autoryzacje MC i AmEx zatwierdzone; logowanie działa (repliki); lista klientów z repliki; zapis klienta 503 z kodem 91; raport obrotów częściowy z `brakujaceSieci: [VISA]`; FR7 503 |
| 6.4 awaria | NFR4: FR7 przy niedostępnym węźle | `BlokadaGlobalnaIT` | zatrzymany AmEx: blokada globalna 503 z siecią AMEX; klient i karta MC bez zmian |
| 6.4 awaria | NFR4: przerwanie w trakcie 2PC | `BlokadaGlobalnaIT` | `COMMIT COMMENT 'ORA-2PC-CRASH-TEST-4'`: ORA-02054, transakcja rozstrzygnięta przez RECO po 134 ms; stan spójny na trzech węzłach |
| 6.5 skalowalność | NFR7: nowa sieć bez zmian w tabelach | `SkalowalnoscIT` | czwarty węzeł (Discover, BIN 6011) z istniejących migracji; dane wspólne na nim po 29 s; karta i autoryzacja na nowym węźle; tabele węzłów 1–3 bez zmian |
| Idempotencja | Ten sam `idZadania` dwa razy | `PlatnosciIT` | jedna transakcja, ten sam RRN, środki zdjęte raz |
| Idempotencja | Równoległe żądania z tym samym `idZadania` | `PlatnosciIT` | 12 żądań: jeden RRN, jedno przetworzenie, środki zdjęte raz |
| Idempotencja | Ten sam `idZadania`, inna kwota | `PlatnosciIT` | 409 z kodem 94 |
| Zwroty | Podwójny zwrot, zwrot ponad kwotę | `PlatnosciIT` | powtórzony zwrot zwraca ten sam RRN; zwrot ponad kwotę: kod 13; po zwrocie całości status ZWROCONA |
| NFR6 | Brak pełnych numerów kart | `PlatnosciIT` | w bazie tylko token HMAC i maska 6+4 |
| FR6 | Numery kart jak u wydawcy | `PlatnosciIT` | karty jednej sieci mają wspólny 8-cyfrowy BIN wydawcy i poprawną sumę Luhna |
| ISO 8583 | jPOS 0100/0110, 0400/0410 | `Iso8583IT` | autoryzacja z RRN i kodem autoryzacji, powtórka z tymi samymi DE42+DE11+DE7 daje ten sam RRN, wycofanie przywraca środki, nieznany akceptant: kod 03 |
| API panelu | FR1–FR11, role, idempotencja | `ApiPaneluIT` | endpointy zgodne z kontraktem; operator nie widzi kart i transakcji innej sieci (403); ten sam `Idempotency-Key` zwraca zapisaną odpowiedź, z innymi danymi 409/94, bez klucza 400 |

Testy, które zatrzymują albo odłączają węzły, przywracają je i czekają, aż repliki będą aktualne.

## Scenariusz obrony pod ruchem

`./demonstracja/scenariusz-obrony.sh --auto` przy generatorze ruchu ok. 20 transakcji na sekundę:

| Krok | Wynik |
|---|---|
| Normalna praca | 18–21 tx/s, 86–92% zatwierdzonych (reszta to celowe odmowy 51, 62, 57, 14), p95 5–7 ms |
| Zatrzymany węzeł Visa | karty Visa: 91 po ok. 4 s (dwie próby połączenia), MC i AmEx 86–97% zatwierdzonych bez przerwy; logowanie do panelu działa; FR7: 503/91/VISA; Visa z powrotem po 30 s |
| MC odcięty od sieci na 52 s | 5 nowych klientów na węźle 1 czekało w MLOG$_KLIENT; po podłączeniu replika miała wszystkich po 20 s, log wyczyszczony |
| Blokada globalna 2PC | klient i karty zablokowane na trzech węzłach, `DBA_2PC_PENDING` puste, płatność zablokowaną kartą: 62 |
| Czwarta sieć | węzeł, migracje i dane gotowe w 84–88 s, 30/30 autoryzacji kartą Discover, tabele MC bez zmian, węzeł usunięty na końcu |
