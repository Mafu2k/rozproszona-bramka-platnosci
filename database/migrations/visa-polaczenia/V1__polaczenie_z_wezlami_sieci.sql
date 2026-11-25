DECLARE
    PROCEDURE utworz_lacze(p_nazwa IN VARCHAR2, p_adres IN VARCHAR2) IS
        v_istnieje NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_istnieje FROM user_db_links WHERE db_link = p_nazwa;
        IF v_istnieje = 0 THEN
            EXECUTE IMMEDIATE 'CREATE DATABASE LINK ' || p_nazwa
                || ' CONNECT TO wezel_link IDENTIFIED BY "${link_haslo}" USING ''' || p_adres || '''';
        END IF;
    END utworz_lacze;
BEGIN
    utworz_lacze('MC', '${adres_mc}');
    utworz_lacze('AMEX', '${adres_amex}');
END;
/

CREATE OR REPLACE SYNONYM karta_mc FOR bramka.karta@mc;
CREATE OR REPLACE SYNONYM karta_amex FOR bramka.karta@amex;
CREATE OR REPLACE SYNONYM transakcja_mc FOR bramka.transakcja@mc;
CREATE OR REPLACE SYNONYM transakcja_amex FOR bramka.transakcja@amex;

CREATE OR REPLACE FORCE VIEW v_transakcja_globalna AS
SELECT 'VISA' AS siec, id_transakcji, id_karty, id_sklepu, id_transakcji_pierwotnej, id_zadania, rrn,
       typ, kwota, waluta, data_transakcji, status, kod_odpowiedzi
  FROM transakcja
UNION ALL
SELECT 'MC', id_transakcji, id_karty, id_sklepu, id_transakcji_pierwotnej, id_zadania, rrn,
       typ, kwota, waluta, data_transakcji, status, kod_odpowiedzi
  FROM transakcja_mc
UNION ALL
SELECT 'AMEX', id_transakcji, id_karty, id_sklepu, id_transakcji_pierwotnej, id_zadania, rrn,
       typ, kwota, waluta, data_transakcji, status, kod_odpowiedzi
  FROM transakcja_amex;

CREATE OR REPLACE FORCE VIEW v_karta_globalna AS
SELECT 'VISA' AS siec, id_karty, id_klienta, id_sieci, numer_maskowany, data_waznosci,
       limit_kredytowy, dostepne_srodki, waluta, status, data_wydania
  FROM karta
UNION ALL
SELECT 'MC', id_karty, id_klienta, id_sieci, numer_maskowany, data_waznosci,
       limit_kredytowy, dostepne_srodki, waluta, status, data_wydania
  FROM karta_mc
UNION ALL
SELECT 'AMEX', id_karty, id_klienta, id_sieci, numer_maskowany, data_waznosci,
       limit_kredytowy, dostepne_srodki, waluta, status, data_wydania
  FROM karta_amex;

CREATE OR REPLACE PACKAGE pkg_klienci AS
    PROCEDURE zablokuj_we_wszystkich_sieciach(
        p_id_klienta IN  klient.id_klienta%TYPE,
        o_visa       OUT NUMBER,
        o_mc         OUT NUMBER,
        o_amex       OUT NUMBER);

    PROCEDURE usun(p_id_klienta IN klient.id_klienta%TYPE);
END pkg_klienci;
/

CREATE OR REPLACE PACKAGE BODY pkg_klienci AS

    PROCEDURE zglos_niedostepnosc(p_kod IN NUMBER, p_siec IN VARCHAR2) IS
    BEGIN
        IF p_kod IN (-2068, -2063, -2019, -12154, -12262, -12505, -12514, -12528, -12541, -12543, -12545,
                     -12170, -3113, -3114, -3135, -1033, -1034, -1089) THEN
            RAISE_APPLICATION_ERROR(-20091, 'Siec niedostepna: ' || p_siec, TRUE);
        END IF;
    END zglos_niedostepnosc;

    FUNCTION zablokuj_wiersz(p_id_klienta IN klient.id_klienta%TYPE) RETURN klient.status%TYPE IS
        v_status klient.status%TYPE;
    BEGIN
        SELECT status INTO v_status FROM klient WHERE id_klienta = p_id_klienta FOR UPDATE;
        RETURN v_status;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(-20044, 'Klient nie istnieje');
    END zablokuj_wiersz;

    PROCEDURE zablokuj_we_wszystkich_sieciach(
        p_id_klienta IN  klient.id_klienta%TYPE,
        o_visa       OUT NUMBER,
        o_mc         OUT NUMBER,
        o_amex       OUT NUMBER
    ) IS
    BEGIN
        IF zablokuj_wiersz(p_id_klienta) = 'USUNIETY' THEN
            RAISE_APPLICATION_ERROR(-20044, 'Klient nie istnieje');
        END IF;

        UPDATE klient SET status = 'ZABLOKOWANY' WHERE id_klienta = p_id_klienta;

        UPDATE karta SET status = 'ZABLOKOWANA' WHERE id_klienta = p_id_klienta AND status = 'AKTYWNA';
        o_visa := SQL%ROWCOUNT;

        BEGIN
            UPDATE karta_mc SET status = 'ZABLOKOWANA' WHERE id_klienta = p_id_klienta AND status = 'AKTYWNA';
            o_mc := SQL%ROWCOUNT;
        EXCEPTION
            WHEN OTHERS THEN
                zglos_niedostepnosc(SQLCODE, 'MC');
                RAISE;
        END;

        BEGIN
            UPDATE karta_amex SET status = 'ZABLOKOWANA' WHERE id_klienta = p_id_klienta AND status = 'AKTYWNA';
            o_amex := SQL%ROWCOUNT;
        EXCEPTION
            WHEN OTHERS THEN
                zglos_niedostepnosc(SQLCODE, 'AMEX');
                RAISE;
        END;
    END zablokuj_we_wszystkich_sieciach;

    PROCEDURE usun(p_id_klienta IN klient.id_klienta%TYPE) IS
        v_aktywne NUMBER;
        v_zdalne  NUMBER;
    BEGIN
        IF zablokuj_wiersz(p_id_klienta) = 'USUNIETY' THEN
            RETURN;
        END IF;

        SELECT COUNT(*) INTO v_aktywne FROM karta WHERE id_klienta = p_id_klienta AND status = 'AKTYWNA';

        BEGIN
            SELECT COUNT(*) INTO v_zdalne FROM karta_mc WHERE id_klienta = p_id_klienta AND status = 'AKTYWNA';
            v_aktywne := v_aktywne + v_zdalne;
        EXCEPTION
            WHEN OTHERS THEN
                zglos_niedostepnosc(SQLCODE, 'MC');
                RAISE;
        END;

        BEGIN
            SELECT COUNT(*) INTO v_zdalne FROM karta_amex WHERE id_klienta = p_id_klienta AND status = 'AKTYWNA';
            v_aktywne := v_aktywne + v_zdalne;
        EXCEPTION
            WHEN OTHERS THEN
                zglos_niedostepnosc(SQLCODE, 'AMEX');
                RAISE;
        END;

        IF v_aktywne > 0 THEN
            RAISE_APPLICATION_ERROR(-20043, 'Klient ma aktywne karty');
        END IF;

        UPDATE klient SET status = 'USUNIETY' WHERE id_klienta = p_id_klienta;
    END usun;

END pkg_klienci;
/
