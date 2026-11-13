DECLARE
    v_istnieje NUMBER;

    PROCEDURE utworz_replike(p_tabela IN VARCHAR2) IS
        v_jest NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_jest FROM user_mviews WHERE mview_name = p_tabela;
        IF v_jest = 0 THEN
            EXECUTE IMMEDIATE 'CREATE MATERIALIZED VIEW ' || p_tabela
                || ' REFRESH FAST ON DEMAND WITH PRIMARY KEY'
                || ' AS SELECT * FROM bramka.' || p_tabela || '@visa';
        END IF;
    END utworz_replike;
BEGIN
    SELECT COUNT(*) INTO v_istnieje FROM user_db_links WHERE db_link = 'VISA';
    IF v_istnieje = 0 THEN
        EXECUTE IMMEDIATE 'CREATE DATABASE LINK visa CONNECT TO wezel_link IDENTIFIED BY "${link_haslo}" USING ''${adres_visa}''';
    END IF;

    utworz_replike('SIEC_KARTOWA');
    utworz_replike('ZAKRES_BIN');
    utworz_replike('KLIENT');
    utworz_replike('SKLEP');
    utworz_replike('UZYTKOWNIK');
END;
/

CREATE INDEX IF NOT EXISTS klient_nazwisko_ix ON klient (UPPER(nazwisko));
CREATE INDEX IF NOT EXISTS klient_email_ix ON klient (email);
CREATE INDEX IF NOT EXISTS sklep_klucz_api_ix ON sklep (hash_klucza_api);
CREATE INDEX IF NOT EXISTS uzytkownik_login_ix ON uzytkownik (login);

CREATE OR REPLACE SYNONYM klient_master FOR bramka.klient@visa;

DECLARE
    v_istnieje NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_istnieje FROM user_refresh WHERE rname = 'GRP_DANE_WSPOLNE';
    IF v_istnieje = 0 THEN
        DBMS_REFRESH.MAKE(
            name      => 'GRP_DANE_WSPOLNE',
            list      => 'SIEC_KARTOWA,ZAKRES_BIN,KLIENT,SKLEP,UZYTKOWNIK',
            next_date => NULL,
            interval  => NULL);
    END IF;

    SELECT COUNT(*) INTO v_istnieje FROM user_scheduler_jobs WHERE job_name = 'ODSWIEZ_DANE_WSPOLNE';
    IF v_istnieje = 0 THEN
        DBMS_SCHEDULER.CREATE_JOB(
            job_name        => 'ODSWIEZ_DANE_WSPOLNE',
            job_type        => 'PLSQL_BLOCK',
            job_action      => 'BEGIN DBMS_REFRESH.REFRESH(''GRP_DANE_WSPOLNE''); END;',
            start_date      => SYSTIMESTAMP,
            repeat_interval => 'FREQ=SECONDLY;INTERVAL=30',
            enabled         => FALSE,
            comments        => 'Przyrostowe odswiezanie replik danych wspolnych z wezla Visa');
        DBMS_SCHEDULER.SET_ATTRIBUTE('ODSWIEZ_DANE_WSPOLNE', 'logging_level', DBMS_SCHEDULER.LOGGING_FAILED_RUNS);
        DBMS_SCHEDULER.ENABLE('ODSWIEZ_DANE_WSPOLNE');
    END IF;
END;
/
