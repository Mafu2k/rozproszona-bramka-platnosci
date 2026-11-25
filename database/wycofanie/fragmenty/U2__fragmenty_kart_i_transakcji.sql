DECLARE
    v_istnieje NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_istnieje FROM user_scheduler_jobs WHERE job_name = 'CZYSC_ZADANIA_PANELU';
    IF v_istnieje > 0 THEN
        DBMS_SCHEDULER.DROP_JOB('CZYSC_ZADANIA_PANELU', force => TRUE);
    END IF;
END;
/
DROP TABLE IF EXISTS zadanie_panelu PURGE;
DROP TABLE IF EXISTS transakcja PURGE;
DROP TABLE IF EXISTS karta PURGE;
DROP SEQUENCE IF EXISTS rrn_seq;
DELETE FROM "flyway_schema_history" WHERE "version" = '2';
COMMIT;
