CREATE TABLE IF NOT EXISTS karta (
    id_karty        NUMBER GENERATED ALWAYS AS IDENTITY (START WITH ${nr_wezla} INCREMENT BY 10)
                    CONSTRAINT karta_pk PRIMARY KEY,
    id_klienta      NUMBER        NOT NULL,
    id_sieci        NUMBER(2)     NOT NULL CONSTRAINT karta_siec_ck CHECK (id_sieci = ${nr_wezla}),
    numer_maskowany VARCHAR2(19)  NOT NULL,
    token           CHAR(64)      NOT NULL CONSTRAINT karta_token_uk UNIQUE,
    data_waznosci   DATE          NOT NULL,
    limit_kredytowy NUMBER(12, 2) NOT NULL CONSTRAINT karta_limit_ck CHECK (limit_kredytowy >= 0),
    dostepne_srodki NUMBER(12, 2) NOT NULL CONSTRAINT karta_srodki_ck CHECK (dostepne_srodki >= 0),
    waluta          CHAR(3)       DEFAULT 'PLN' NOT NULL,
    status          VARCHAR2(12)  DEFAULT 'AKTYWNA' NOT NULL
                    CONSTRAINT karta_status_ck CHECK (status IN ('AKTYWNA', 'ZABLOKOWANA')),
    data_wydania    TIMESTAMP(3)  DEFAULT SYS_EXTRACT_UTC(SYSTIMESTAMP) NOT NULL
);

CREATE TABLE IF NOT EXISTS transakcja (
    id_transakcji            NUMBER GENERATED ALWAYS AS IDENTITY (START WITH ${nr_wezla} INCREMENT BY 10)
                             CONSTRAINT transakcja_pk PRIMARY KEY,
    id_karty                 NUMBER        NOT NULL CONSTRAINT transakcja_karta_fk REFERENCES karta (id_karty),
    id_sklepu                NUMBER        NOT NULL,
    id_transakcji_pierwotnej NUMBER        CONSTRAINT transakcja_pierwotna_fk REFERENCES transakcja (id_transakcji),
    id_zadania               VARCHAR2(64)  NOT NULL,
    rrn                      CHAR(12)      NOT NULL CONSTRAINT transakcja_rrn_uk UNIQUE,
    typ                      VARCHAR2(12)  NOT NULL CONSTRAINT transakcja_typ_ck CHECK (typ IN ('AUTORYZACJA', 'ZWROT')),
    kwota                    NUMBER(12, 2) NOT NULL CONSTRAINT transakcja_kwota_ck CHECK (kwota > 0),
    waluta                   CHAR(3)       NOT NULL,
    data_transakcji          TIMESTAMP(3)  DEFAULT SYS_EXTRACT_UTC(SYSTIMESTAMP) NOT NULL,
    status                   VARCHAR2(12)  NOT NULL
                             CONSTRAINT transakcja_status_ck CHECK (status IN ('AUTORYZOWANA', 'ODRZUCONA', 'ZWROCONA')),
    kod_odpowiedzi           CHAR(2)       NOT NULL,
    CONSTRAINT transakcja_zadanie_uk UNIQUE (id_sklepu, id_zadania),
    CONSTRAINT transakcja_zwrot_ck CHECK (
        (typ = 'AUTORYZACJA' AND id_transakcji_pierwotnej IS NULL)
        OR (typ = 'ZWROT' AND id_transakcji_pierwotnej IS NOT NULL)
    )
);

CREATE TABLE IF NOT EXISTS zadanie_panelu (
    id_uzytkownika  NUMBER       NOT NULL,
    klucz           VARCHAR2(64) NOT NULL,
    odcisk          CHAR(64)     NOT NULL,
    status_http     NUMBER(3),
    odpowiedz       CLOB,
    data_utworzenia TIMESTAMP(3) DEFAULT SYS_EXTRACT_UTC(SYSTIMESTAMP) NOT NULL,
    CONSTRAINT zadanie_panelu_pk PRIMARY KEY (id_uzytkownika, klucz)
);

CREATE INDEX IF NOT EXISTS karta_klient_ix ON karta (id_klienta);
CREATE INDEX IF NOT EXISTS transakcja_sklep_data_ix ON transakcja (id_sklepu, data_transakcji);
CREATE INDEX IF NOT EXISTS transakcja_karta_data_ix ON transakcja (id_karty, data_transakcji);
CREATE INDEX IF NOT EXISTS transakcja_pierwotna_ix ON transakcja (id_transakcji_pierwotnej);
CREATE INDEX IF NOT EXISTS transakcja_data_ix ON transakcja (data_transakcji);
CREATE INDEX IF NOT EXISTS zadanie_panelu_data_ix ON zadanie_panelu (data_utworzenia);

CREATE SEQUENCE IF NOT EXISTS rrn_seq START WITH 1 INCREMENT BY 1 MAXVALUE 9999999 CYCLE CACHE 100;

GRANT SELECT ON karta TO wezel_link;
GRANT UPDATE (status) ON karta TO wezel_link;
GRANT SELECT ON transakcja TO wezel_link;

DECLARE
    v_istnieje NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_istnieje FROM user_scheduler_jobs WHERE job_name = 'CZYSC_ZADANIA_PANELU';
    IF v_istnieje = 0 THEN
        DBMS_SCHEDULER.CREATE_JOB(
            job_name        => 'CZYSC_ZADANIA_PANELU',
            job_type        => 'PLSQL_BLOCK',
            job_action      => 'BEGIN DELETE FROM zadanie_panelu WHERE data_utworzenia < SYS_EXTRACT_UTC(SYSTIMESTAMP) - INTERVAL ''7'' DAY; COMMIT; END;',
            start_date      => SYSTIMESTAMP,
            repeat_interval => 'FREQ=DAILY;BYHOUR=3',
            enabled         => TRUE,
            comments        => 'Usuwanie kluczy idempotencji panelu starszych niz 7 dni');
    END IF;
END;
/
