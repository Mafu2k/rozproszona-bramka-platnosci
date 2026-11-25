# Przykładowe zapytania rozproszone

Zapytania uruchamiane na węźle Visa (kopia główna), chyba że opis mówi inaczej.

## 1. Rekonstrukcja fragmentacji poziomej: wszystkie transakcje jako suma fragmentów trzech węzłów

```sql
SELECT siec, COUNT(*) AS liczba, SUM(kwota) AS suma
  FROM v_transakcja_globalna
 GROUP BY siec
 ORDER BY siec;
```

## 2. Rozłączność fragmentów: żaden identyfikator nie powtarza się między węzłami (IDENTITY z krokiem 10)

```sql
SELECT id_transakcji, COUNT(*)
  FROM v_transakcja_globalna
 GROUP BY id_transakcji
HAVING COUNT(*) > 1;
```

## 3. Kompletność: każda karta należy do sieci swojego węzła (fragmentacja pierwotna)

```sql
SELECT g.siec, s.kod, COUNT(*)
  FROM v_karta_globalna g
  JOIN siec_kartowa s ON s.id_sieci = g.id_sieci
 GROUP BY g.siec, s.kod;
```

## 4. Złączenie rozproszone: klient z jego kartami we wszystkich sieciach (dane wspólne + fragmenty)

```sql
SELECT k.imie, k.nazwisko, g.siec, g.numer_maskowany, g.status, g.dostepne_srodki
  FROM klient k
  JOIN v_karta_globalna g ON g.id_klienta = k.id_klienta
 WHERE k.email = 'anna.kowalska@example.com'
 ORDER BY g.siec;
```

## 5. Obroty sklepów w całym systemie (agregacja na danych z trzech węzłów)

```sql
SELECT s.nazwa, g.siec,
       COUNT(CASE WHEN g.typ = 'AUTORYZACJA' AND g.kod_odpowiedzi = '00' THEN 1 END) AS zatwierdzone,
       NVL(SUM(CASE WHEN g.typ = 'AUTORYZACJA' AND g.kod_odpowiedzi = '00' THEN g.kwota END), 0) AS obrot,
       NVL(SUM(CASE WHEN g.typ = 'ZWROT' AND g.kod_odpowiedzi = '00' THEN g.kwota END), 0) AS zwroty
  FROM v_transakcja_globalna g
  JOIN sklep s ON s.id_sklepu = g.id_sklepu
 GROUP BY s.nazwa, g.siec
 ORDER BY s.nazwa, g.siec;
```

## 6. Odsetek odrzuconych autoryzacji w każdej sieci

```sql
SELECT siec,
       COUNT(*) AS autoryzacje,
       ROUND(100 * COUNT(CASE WHEN status = 'ODRZUCONA' THEN 1 END) / NULLIF(COUNT(*), 0), 2) AS odsetek_odrzuconych
  FROM v_transakcja_globalna
 WHERE typ = 'AUTORYZACJA'
 GROUP BY siec;
```

## 7. Transakcja z jej zwrotami po numerze RRN (relacja transakcja 1:N transakcja)

```sql
SELECT p.rrn AS rrn_pierwotnej, p.kwota AS kwota_pierwotna, p.status AS status_pierwotnej,
       z.rrn AS rrn_zwrotu, z.kwota AS kwota_zwrotu, z.kod_odpowiedzi
  FROM v_transakcja_globalna p
  LEFT JOIN v_transakcja_globalna z ON z.id_transakcji_pierwotnej = p.id_transakcji
 WHERE p.typ = 'AUTORYZACJA'
   AND p.status = 'ZWROCONA';
```

## 8. Zapis przez łącze bazodanowe w jednej transakcji rozproszonej (2PC przy COMMIT)

```sql
BEGIN
    UPDATE karta_mc SET status = 'ZABLOKOWANA' WHERE id_klienta = 1 AND status = 'AKTYWNA';
    UPDATE karta_amex SET status = 'ZABLOKOWANA' WHERE id_klienta = 1 AND status = 'AKTYWNA';
    COMMIT;
END;
```

## 9. Węzeł MasterCard lub AmEx: aktualność replik danych wspólnych (wymaganie 60 s)

```sql
SELECT mview_name, last_refresh_type, last_refresh_date,
       ROUND((SYSDATE - last_refresh_date) * 86400) AS sekund_temu
  FROM user_mviews
 ORDER BY mview_name;
```

## 10. Węzeł MasterCard lub AmEx: ostatnie nieudane odświeżenia replik

```sql
SELECT log_date, status, additional_info
  FROM user_scheduler_job_run_details
 WHERE job_name = 'ODSWIEZ_DANE_WSPOLNE'
 ORDER BY log_date DESC
 FETCH FIRST 5 ROWS ONLY;
```

## 11. Dowolny węzeł: transakcje rozproszone w stanie niepewnym (in-doubt)

```sql
SELECT local_tran_id, global_tran_id, state, mixed, fail_time
  FROM sys.dba_2pc_pending;
```

## 12. Węzeł Visa: rozmiar logów widoków zmaterializowanych (zmiany czekające na repliki)

```sql
SELECT l.master, s.bytes
  FROM user_mview_logs l
  JOIN user_segments s ON s.segment_name = l.log_table;
```
