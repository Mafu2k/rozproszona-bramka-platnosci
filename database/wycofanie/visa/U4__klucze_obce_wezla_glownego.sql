DECLARE
    PROCEDURE usun_ograniczenie(p_tabela IN VARCHAR2, p_nazwa IN VARCHAR2) IS
        v_istnieje NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_istnieje FROM user_constraints WHERE constraint_name = p_nazwa;
        IF v_istnieje > 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE ' || p_tabela || ' DROP CONSTRAINT ' || p_nazwa;
        END IF;
    END usun_ograniczenie;
BEGIN
    usun_ograniczenie('TRANSAKCJA', 'TRANSAKCJA_SKLEP_FK');
    usun_ograniczenie('KARTA', 'KARTA_KLIENT_FK');
END;
/
DELETE FROM "flyway_schema_history" WHERE "version" = '4';
COMMIT;
