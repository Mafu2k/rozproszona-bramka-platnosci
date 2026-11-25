DECLARE
    PROCEDURE dodaj_ograniczenie(p_nazwa IN VARCHAR2, p_ddl IN VARCHAR2) IS
        v_istnieje NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_istnieje FROM user_constraints WHERE constraint_name = p_nazwa;
        IF v_istnieje = 0 THEN
            EXECUTE IMMEDIATE p_ddl;
        END IF;
    END dodaj_ograniczenie;
BEGIN
    dodaj_ograniczenie('KARTA_KLIENT_FK',
        'ALTER TABLE karta ADD CONSTRAINT karta_klient_fk FOREIGN KEY (id_klienta) REFERENCES klient (id_klienta)');
    dodaj_ograniczenie('TRANSAKCJA_SKLEP_FK',
        'ALTER TABLE transakcja ADD CONSTRAINT transakcja_sklep_fk FOREIGN KEY (id_sklepu) REFERENCES sklep (id_sklepu)');
END;
/
