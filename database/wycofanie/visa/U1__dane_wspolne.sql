DECLARE
    PROCEDURE usun_log(p_tabela IN VARCHAR2) IS
        v_istnieje NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_istnieje FROM user_mview_logs WHERE master = p_tabela;
        IF v_istnieje > 0 THEN
            EXECUTE IMMEDIATE 'DROP MATERIALIZED VIEW LOG ON ' || p_tabela;
        END IF;
    END usun_log;
BEGIN
    usun_log('UZYTKOWNIK');
    usun_log('SKLEP');
    usun_log('KLIENT');
    usun_log('ZAKRES_BIN');
    usun_log('SIEC_KARTOWA');
END;
/
DROP SYNONYM IF EXISTS klient_master;
DROP TRIGGER IF EXISTS zakres_bin_rozlacznosc_trg;
DROP TABLE IF EXISTS uzytkownik PURGE;
DROP TABLE IF EXISTS sklep PURGE;
DROP TABLE IF EXISTS klient PURGE;
DROP TABLE IF EXISTS zakres_bin PURGE;
DROP TABLE IF EXISTS siec_kartowa PURGE;
DELETE FROM "flyway_schema_history" WHERE "version" = '1' OR "script" = 'R__dane_demo.sql';
COMMIT;
