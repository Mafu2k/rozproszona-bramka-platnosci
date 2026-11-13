MERGE INTO klient d
USING (
    SELECT 'Anna' AS imie, 'Kowalska' AS nazwisko, 'anna.kowalska@example.com' AS email, '+48501100200' AS telefon, 'PL' AS kraj FROM dual UNION ALL
    SELECT 'Piotr', 'Nowak', 'piotr.nowak@example.com', '+48502200300', 'PL' FROM dual UNION ALL
    SELECT 'Katarzyna', 'Wiśniewska', 'katarzyna.wisniewska@example.com', '+48503300400', 'PL' FROM dual UNION ALL
    SELECT 'Tomasz', 'Wójcik', 'tomasz.wojcik@example.com', '+48504400500', 'PL' FROM dual UNION ALL
    SELECT 'Magdalena', 'Kamińska', 'magdalena.kaminska@example.com', '+48505500600', 'PL' FROM dual UNION ALL
    SELECT 'Jan', 'Lewandowski', 'jan.lewandowski@example.com', '+48506600700', 'PL' FROM dual UNION ALL
    SELECT 'Ewa', 'Zielińska', 'ewa.zielinska@example.com', '+48507700800', 'PL' FROM dual UNION ALL
    SELECT 'Hans', 'Müller', 'hans.mueller@example.com', '+4915112345678', 'DE' FROM dual
) s
ON (d.email = s.email)
WHEN NOT MATCHED THEN
    INSERT (imie, nazwisko, email, telefon, kraj) VALUES (s.imie, s.nazwisko, s.email, s.telefon, s.kraj);

MERGE INTO sklep d
USING (
    SELECT 'Elektro Śląsk' AS nazwa, '5260181596' AS nip, '5732' AS kategoria_mcc, 'https://elektro-slask.example.com' AS url, 'kontakt@elektro-slask.example.com' AS email_kontaktowy FROM dual UNION ALL
    SELECT 'Księgarnia Pod Lipą', '1860913901', '5942', 'https://podlipa.example.com', 'sklep@podlipa.example.com' FROM dual UNION ALL
    SELECT 'Delikatesy Familia', '6281948218', '5411', 'https://familia.example.com', 'biuro@familia.example.com' FROM dual UNION ALL
    SELECT 'Pizzeria Gliwicka', '9935181904', '5812', 'https://pizzeria-gliwicka.example.com', 'zamowienia@pizzeria-gliwicka.example.com' FROM dual UNION ALL
    SELECT 'Bilety Metropolia', '9378657972', '4111', 'https://bilety-metropolia.example.com', 'pomoc@bilety-metropolia.example.com' FROM dual UNION ALL
    SELECT 'Sport Max', '5432319484', '5941', 'https://sportmax.example.com', 'kontakt@sportmax.example.com' FROM dual
) s
ON (d.nip = s.nip)
WHEN NOT MATCHED THEN
    INSERT (nazwa, nip, kategoria_mcc, url, email_kontaktowy)
    VALUES (s.nazwa, s.nip, s.kategoria_mcc, s.url, s.email_kontaktowy);

COMMIT;
