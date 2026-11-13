CREATE OR REPLACE PACKAGE pkg_autoryzacja AS
    PROCEDURE autoryzuj(
        p_token         IN  karta.token%TYPE,
        p_id_sklepu     IN  transakcja.id_sklepu%TYPE,
        p_id_zadania    IN  transakcja.id_zadania%TYPE,
        p_kwota         IN  transakcja.kwota%TYPE,
        p_waluta        IN  transakcja.waluta%TYPE,
        o_id_transakcji OUT NUMBER,
        o_rrn           OUT VARCHAR2,
        o_status        OUT VARCHAR2,
        o_kod           OUT VARCHAR2,
        o_data          OUT TIMESTAMP,
        o_maska         OUT VARCHAR2,
        o_powtorzona    OUT NUMBER);

    PROCEDURE zwroc(
        p_rrn_pierwotnej IN  transakcja.rrn%TYPE,
        p_id_sklepu      IN  transakcja.id_sklepu%TYPE,
        p_id_zadania     IN  transakcja.id_zadania%TYPE,
        p_kwota          IN  transakcja.kwota%TYPE,
        p_waluta         IN  transakcja.waluta%TYPE,
        o_id_transakcji  OUT NUMBER,
        o_rrn            OUT VARCHAR2,
        o_status         OUT VARCHAR2,
        o_kod            OUT VARCHAR2,
        o_data           OUT TIMESTAMP,
        o_maska          OUT VARCHAR2,
        o_powtorzona     OUT NUMBER);
END pkg_autoryzacja;
/

CREATE OR REPLACE PACKAGE BODY pkg_autoryzacja AS

    FUNCTION nowy_rrn RETURN transakcja.rrn%TYPE IS
    BEGIN
        RETURN TO_CHAR(SYS_EXTRACT_UTC(SYSTIMESTAMP), 'YDDD') || MOD(${nr_wezla}, 10) || LPAD(rrn_seq.NEXTVAL, 7, '0');
    END nowy_rrn;

    PROCEDURE sprawdz_sklep(p_id_sklepu IN transakcja.id_sklepu%TYPE) IS
        v_status sklep.status%TYPE;
    BEGIN
        SELECT status INTO v_status FROM sklep WHERE id_sklepu = p_id_sklepu;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(-20020, 'Dane sklepu w trakcie replikacji');
    END sprawdz_sklep;

    PROCEDURE odrzuc_inne_uzycie_zadania(
        p_id_sklepu  IN transakcja.id_sklepu%TYPE,
        p_id_zadania IN transakcja.id_zadania%TYPE
    ) IS
        v_istnieje NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_istnieje FROM transakcja WHERE id_sklepu = p_id_sklepu AND id_zadania = p_id_zadania;
        IF v_istnieje > 0 THEN
            RAISE_APPLICATION_ERROR(-20094, 'Identyfikator zadania uzyty z innymi danymi');
        END IF;
    END odrzuc_inne_uzycie_zadania;

    FUNCTION zapisana(
        p_id_sklepu     IN  transakcja.id_sklepu%TYPE,
        p_id_zadania    IN  transakcja.id_zadania%TYPE,
        p_id_karty      IN  transakcja.id_karty%TYPE,
        p_typ           IN  transakcja.typ%TYPE,
        p_id_pierwotnej IN  transakcja.id_transakcji_pierwotnej%TYPE,
        p_kwota         IN  transakcja.kwota%TYPE,
        p_waluta        IN  transakcja.waluta%TYPE,
        o_wynik         OUT transakcja%ROWTYPE
    ) RETURN BOOLEAN IS
    BEGIN
        SELECT * INTO o_wynik FROM transakcja WHERE id_sklepu = p_id_sklepu AND id_zadania = p_id_zadania;
        IF o_wynik.id_karty <> p_id_karty
           OR o_wynik.typ <> p_typ
           OR o_wynik.kwota <> p_kwota
           OR o_wynik.waluta <> p_waluta
           OR NVL(o_wynik.id_transakcji_pierwotnej, -1) <> NVL(p_id_pierwotnej, -1) THEN
            RAISE_APPLICATION_ERROR(-20094, 'Identyfikator zadania uzyty z innymi danymi');
        END IF;
        RETURN TRUE;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RETURN FALSE;
    END zapisana;

    PROCEDURE zapisz(io_wynik IN OUT transakcja%ROWTYPE) IS
    BEGIN
        io_wynik.rrn := nowy_rrn;
        INSERT INTO transakcja (
            id_karty, id_sklepu, id_transakcji_pierwotnej, id_zadania, rrn,
            typ, kwota, waluta, status, kod_odpowiedzi)
        VALUES (
            io_wynik.id_karty, io_wynik.id_sklepu, io_wynik.id_transakcji_pierwotnej, io_wynik.id_zadania, io_wynik.rrn,
            io_wynik.typ, io_wynik.kwota, io_wynik.waluta, io_wynik.status, io_wynik.kod_odpowiedzi)
        RETURNING id_transakcji, data_transakcji INTO io_wynik.id_transakcji, io_wynik.data_transakcji;
    END zapisz;

    PROCEDURE przepisz(
        p_wynik         IN  transakcja%ROWTYPE,
        o_id_transakcji OUT NUMBER,
        o_rrn           OUT VARCHAR2,
        o_status        OUT VARCHAR2,
        o_kod           OUT VARCHAR2,
        o_data          OUT TIMESTAMP
    ) IS
    BEGIN
        o_id_transakcji := p_wynik.id_transakcji;
        o_rrn := p_wynik.rrn;
        o_status := p_wynik.status;
        o_kod := p_wynik.kod_odpowiedzi;
        o_data := p_wynik.data_transakcji;
    END przepisz;

    PROCEDURE autoryzuj(
        p_token         IN  karta.token%TYPE,
        p_id_sklepu     IN  transakcja.id_sklepu%TYPE,
        p_id_zadania    IN  transakcja.id_zadania%TYPE,
        p_kwota         IN  transakcja.kwota%TYPE,
        p_waluta        IN  transakcja.waluta%TYPE,
        o_id_transakcji OUT NUMBER,
        o_rrn           OUT VARCHAR2,
        o_status        OUT VARCHAR2,
        o_kod           OUT VARCHAR2,
        o_data          OUT TIMESTAMP,
        o_maska         OUT VARCHAR2,
        o_powtorzona    OUT NUMBER
    ) IS
        v_karta karta%ROWTYPE;
        v_wynik transakcja%ROWTYPE;
        v_dzis  DATE := TRUNC(SYS_EXTRACT_UTC(SYSTIMESTAMP));
    BEGIN
        o_powtorzona := 0;
        sprawdz_sklep(p_id_sklepu);

        BEGIN
            SELECT * INTO v_karta FROM karta WHERE token = p_token FOR UPDATE;
        EXCEPTION
            WHEN NO_DATA_FOUND THEN
                odrzuc_inne_uzycie_zadania(p_id_sklepu, p_id_zadania);
                o_status := 'ODRZUCONA';
                o_kod := '14';
                RETURN;
        END;
        o_maska := v_karta.numer_maskowany;

        IF zapisana(p_id_sklepu, p_id_zadania, v_karta.id_karty, 'AUTORYZACJA', NULL, p_kwota, p_waluta, v_wynik) THEN
            ROLLBACK;
            o_powtorzona := 1;
            przepisz(v_wynik, o_id_transakcji, o_rrn, o_status, o_kod, o_data);
            RETURN;
        END IF;

        v_wynik.id_karty := v_karta.id_karty;
        v_wynik.id_sklepu := p_id_sklepu;
        v_wynik.id_zadania := p_id_zadania;
        v_wynik.typ := 'AUTORYZACJA';
        v_wynik.kwota := p_kwota;
        v_wynik.waluta := p_waluta;

        IF v_karta.waluta <> p_waluta THEN
            v_wynik.status := 'ODRZUCONA';
            v_wynik.kod_odpowiedzi := '57';
        ELSE
            UPDATE karta
               SET dostepne_srodki = dostepne_srodki - p_kwota
             WHERE id_karty = v_karta.id_karty
               AND status = 'AKTYWNA'
               AND data_waznosci >= v_dzis
               AND dostepne_srodki >= p_kwota;

            IF SQL%ROWCOUNT = 1 THEN
                v_wynik.status := 'AUTORYZOWANA';
                v_wynik.kod_odpowiedzi := '00';
            ELSIF v_karta.status <> 'AKTYWNA' THEN
                v_wynik.status := 'ODRZUCONA';
                v_wynik.kod_odpowiedzi := '62';
            ELSIF v_karta.data_waznosci < v_dzis THEN
                v_wynik.status := 'ODRZUCONA';
                v_wynik.kod_odpowiedzi := '54';
            ELSE
                v_wynik.status := 'ODRZUCONA';
                v_wynik.kod_odpowiedzi := '51';
            END IF;
        END IF;

        zapisz(v_wynik);
        COMMIT;
        przepisz(v_wynik, o_id_transakcji, o_rrn, o_status, o_kod, o_data);
    EXCEPTION
        WHEN DUP_VAL_ON_INDEX THEN
            ROLLBACK;
            IF NOT zapisana(p_id_sklepu, p_id_zadania, v_karta.id_karty, 'AUTORYZACJA', NULL, p_kwota, p_waluta, v_wynik) THEN
                RAISE;
            END IF;
            o_powtorzona := 1;
            przepisz(v_wynik, o_id_transakcji, o_rrn, o_status, o_kod, o_data);
    END autoryzuj;

    PROCEDURE zwroc(
        p_rrn_pierwotnej IN  transakcja.rrn%TYPE,
        p_id_sklepu      IN  transakcja.id_sklepu%TYPE,
        p_id_zadania     IN  transakcja.id_zadania%TYPE,
        p_kwota          IN  transakcja.kwota%TYPE,
        p_waluta         IN  transakcja.waluta%TYPE,
        o_id_transakcji  OUT NUMBER,
        o_rrn            OUT VARCHAR2,
        o_status         OUT VARCHAR2,
        o_kod            OUT VARCHAR2,
        o_data           OUT TIMESTAMP,
        o_maska          OUT VARCHAR2,
        o_powtorzona     OUT NUMBER
    ) IS
        v_pierwotna transakcja%ROWTYPE;
        v_wynik     transakcja%ROWTYPE;
        v_zwrocono  transakcja.kwota%TYPE;
    BEGIN
        o_powtorzona := 0;
        sprawdz_sklep(p_id_sklepu);

        BEGIN
            SELECT *
              INTO v_pierwotna
              FROM transakcja
             WHERE rrn = p_rrn_pierwotnej
               AND id_sklepu = p_id_sklepu
               AND typ = 'AUTORYZACJA'
               FOR UPDATE;
        EXCEPTION
            WHEN NO_DATA_FOUND THEN
                odrzuc_inne_uzycie_zadania(p_id_sklepu, p_id_zadania);
                o_status := 'ODRZUCONA';
                o_kod := '25';
                RETURN;
        END;
        SELECT numer_maskowany INTO o_maska FROM karta WHERE id_karty = v_pierwotna.id_karty;

        IF zapisana(p_id_sklepu, p_id_zadania, v_pierwotna.id_karty, 'ZWROT', v_pierwotna.id_transakcji,
                    p_kwota, p_waluta, v_wynik) THEN
            ROLLBACK;
            o_powtorzona := 1;
            przepisz(v_wynik, o_id_transakcji, o_rrn, o_status, o_kod, o_data);
            RETURN;
        END IF;

        SELECT NVL(SUM(kwota), 0)
          INTO v_zwrocono
          FROM transakcja
         WHERE id_transakcji_pierwotnej = v_pierwotna.id_transakcji
           AND status = 'AUTORYZOWANA';

        v_wynik.id_karty := v_pierwotna.id_karty;
        v_wynik.id_sklepu := p_id_sklepu;
        v_wynik.id_transakcji_pierwotnej := v_pierwotna.id_transakcji;
        v_wynik.id_zadania := p_id_zadania;
        v_wynik.typ := 'ZWROT';
        v_wynik.kwota := p_kwota;
        v_wynik.waluta := p_waluta;

        IF v_pierwotna.status = 'ODRZUCONA' THEN
            v_wynik.status := 'ODRZUCONA';
            v_wynik.kod_odpowiedzi := '12';
        ELSIF p_waluta <> v_pierwotna.waluta OR v_zwrocono + p_kwota > v_pierwotna.kwota THEN
            v_wynik.status := 'ODRZUCONA';
            v_wynik.kod_odpowiedzi := '13';
        ELSE
            UPDATE karta
               SET dostepne_srodki = dostepne_srodki + p_kwota
             WHERE id_karty = v_pierwotna.id_karty;
            IF v_zwrocono + p_kwota = v_pierwotna.kwota THEN
                UPDATE transakcja SET status = 'ZWROCONA' WHERE id_transakcji = v_pierwotna.id_transakcji;
            END IF;
            v_wynik.status := 'AUTORYZOWANA';
            v_wynik.kod_odpowiedzi := '00';
        END IF;

        zapisz(v_wynik);
        COMMIT;
        przepisz(v_wynik, o_id_transakcji, o_rrn, o_status, o_kod, o_data);
    EXCEPTION
        WHEN DUP_VAL_ON_INDEX THEN
            ROLLBACK;
            IF NOT zapisana(p_id_sklepu, p_id_zadania, v_pierwotna.id_karty, 'ZWROT', v_pierwotna.id_transakcji,
                            p_kwota, p_waluta, v_wynik) THEN
                RAISE;
            END IF;
            o_powtorzona := 1;
            przepisz(v_wynik, o_id_transakcji, o_rrn, o_status, o_kod, o_data);
    END zwroc;

END pkg_autoryzacja;
/

CREATE OR REPLACE PACKAGE pkg_karty AS
    PROCEDURE wydaj(
        p_id_klienta    IN  karta.id_klienta%TYPE,
        p_token         IN  karta.token%TYPE,
        p_maska         IN  karta.numer_maskowany%TYPE,
        p_data_waznosci IN  karta.data_waznosci%TYPE,
        p_limit         IN  karta.limit_kredytowy%TYPE,
        p_waluta        IN  karta.waluta%TYPE,
        o_id_karty      OUT NUMBER);

    PROCEDURE zmien_limit(p_id_karty IN karta.id_karty%TYPE, p_limit IN karta.limit_kredytowy%TYPE);

    PROCEDURE zablokuj(p_id_karty IN karta.id_karty%TYPE);

    PROCEDURE odblokuj(p_id_karty IN karta.id_karty%TYPE);
END pkg_karty;
/

CREATE OR REPLACE PACKAGE BODY pkg_karty AS

    FUNCTION status_klienta_w_master(p_id_klienta IN karta.id_klienta%TYPE) RETURN klient.status%TYPE IS
        v_status klient.status%TYPE;
    BEGIN
        SELECT status INTO v_status FROM klient_master WHERE id_klienta = p_id_klienta FOR UPDATE;
        RETURN v_status;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(-20044, 'Klient nie istnieje');
        WHEN OTHERS THEN
            IF SQLCODE IN (-2068, -2063, -2019, -12154, -12262, -12505, -12514, -12528, -12541, -12543, -12545,
                           -12170, -3113, -3114, -3135, -1033, -1034, -1089) THEN
                RAISE_APPLICATION_ERROR(-20091, 'Siec niedostepna: VISA', TRUE);
            END IF;
            RAISE;
    END status_klienta_w_master;

    FUNCTION karta_do_zmiany(p_id_karty IN karta.id_karty%TYPE) RETURN karta%ROWTYPE IS
        v_karta karta%ROWTYPE;
    BEGIN
        SELECT * INTO v_karta FROM karta WHERE id_karty = p_id_karty FOR UPDATE;
        RETURN v_karta;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(-20047, 'Karta nie istnieje');
    END karta_do_zmiany;

    PROCEDURE wydaj(
        p_id_klienta    IN  karta.id_klienta%TYPE,
        p_token         IN  karta.token%TYPE,
        p_maska         IN  karta.numer_maskowany%TYPE,
        p_data_waznosci IN  karta.data_waznosci%TYPE,
        p_limit         IN  karta.limit_kredytowy%TYPE,
        p_waluta        IN  karta.waluta%TYPE,
        o_id_karty      OUT NUMBER
    ) IS
        v_w_replice NUMBER;
    BEGIN
        IF status_klienta_w_master(p_id_klienta) <> 'AKTYWNY' THEN
            RAISE_APPLICATION_ERROR(-20045, 'Klient nie jest aktywny');
        END IF;

        SELECT COUNT(*) INTO v_w_replice FROM klient WHERE id_klienta = p_id_klienta;
        IF v_w_replice = 0 THEN
            RAISE_APPLICATION_ERROR(-20020, 'Dane klienta w trakcie replikacji');
        END IF;

        INSERT INTO karta (
            id_klienta, id_sieci, numer_maskowany, token, data_waznosci,
            limit_kredytowy, dostepne_srodki, waluta)
        VALUES (
            p_id_klienta, ${nr_wezla}, p_maska, p_token, p_data_waznosci,
            p_limit, p_limit, p_waluta)
        RETURNING id_karty INTO o_id_karty;
    END wydaj;

    PROCEDURE zmien_limit(p_id_karty IN karta.id_karty%TYPE, p_limit IN karta.limit_kredytowy%TYPE) IS
        v_karta        karta%ROWTYPE := karta_do_zmiany(p_id_karty);
        v_wykorzystane karta.limit_kredytowy%TYPE := v_karta.limit_kredytowy - v_karta.dostepne_srodki;
    BEGIN
        IF p_limit < v_wykorzystane THEN
            RAISE_APPLICATION_ERROR(-20046, 'Nowy limit jest nizszy niz wykorzystane srodki');
        END IF;
        UPDATE karta
           SET limit_kredytowy = p_limit,
               dostepne_srodki = p_limit - v_wykorzystane
         WHERE id_karty = p_id_karty;
    END zmien_limit;

    PROCEDURE zablokuj(p_id_karty IN karta.id_karty%TYPE) IS
        v_karta karta%ROWTYPE := karta_do_zmiany(p_id_karty);
    BEGIN
        UPDATE karta SET status = 'ZABLOKOWANA' WHERE id_karty = v_karta.id_karty;
    END zablokuj;

    PROCEDURE odblokuj(p_id_karty IN karta.id_karty%TYPE) IS
        v_karta karta%ROWTYPE := karta_do_zmiany(p_id_karty);
    BEGIN
        IF status_klienta_w_master(v_karta.id_klienta) <> 'AKTYWNY' THEN
            RAISE_APPLICATION_ERROR(-20048, 'Klient jest zablokowany lub usuniety');
        END IF;
        UPDATE karta SET status = 'AKTYWNA' WHERE id_karty = v_karta.id_karty;
    END odblokuj;

END pkg_karty;
/
