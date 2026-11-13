package pl.janicki.bramka.bledy;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.sql.SQLRecoverableException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.UncategorizedSQLException;

class BledyBazyTest {

    private static RuntimeException opakuj(SQLException blad) {
        return new UncategorizedSQLException("test", "CALL x", blad);
    }

    @Test
    void niedostepneLaczeWskazujeZdalnaSiec() {
        SQLException blad = new SQLException(
                "ORA-12541: Cannot connect. No listener at host amex-db port 1521.\nORA-02063: preceding line from AMEX", "08006", 12541);
        BladApi wynik = (BladApi) BledyBazy.przetlumacz(opakuj(blad), "VISA");
        assertThat(wynik.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(wynik.kod()).isEqualTo("91");
        assertThat(wynik.szczegoly()).containsEntry("siec", "AMEX");
    }

    @Test
    void zerwanePolaczenieZWezlemWskazujeTenWezel() {
        BladApi wynik = (BladApi) BledyBazy.przetlumacz(opakuj(new SQLRecoverableException("IO Error", "08006", 17002)), "MC");
        assertThat(wynik.kod()).isEqualTo("91");
        assertThat(wynik.szczegoly()).containsEntry("siec", "MC");
    }

    @Test
    void bladAplikacyjnyIdempotencjiDajeKonflikt94() {
        SQLException blad = new SQLException("ORA-20094: Identyfikator zadania uzyty z innymi danymi", "72000", 20094);
        BladApi wynik = (BladApi) BledyBazy.przetlumacz(opakuj(blad), "VISA");
        assertThat(wynik.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(wynik.kod()).isEqualTo("94");
    }

    @Test
    void bladAplikacyjnyOpakowanyWTransakcjiRozproszonejJestRozpoznawany() {
        SQLException blad = new SQLException("""
                ORA-02055: distributed update operation failed; rollback required
                ORA-20020: Dane klienta w trakcie replikacji
                ORA-06512: at "BRAMKA.PKG_KARTY", line 40""", "42000", 2055);
        BladApi wynik = (BladApi) BledyBazy.przetlumacz(opakuj(blad), "MC");
        assertThat(wynik.kod()).isEqualTo("REPLIKACJA_W_TOKU");
    }

    @Test
    void zdalnyBladAplikacyjnyNieJestTraktowanyJakoAwariaLacza() {
        SQLException blad = new SQLException("ORA-20047: Karta nie istnieje\nORA-02063: preceding 2 lines from MC", "72000", 20047);
        BladApi wynik = (BladApi) BledyBazy.przetlumacz(opakuj(blad), "VISA");
        assertThat(wynik.status()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void awariaLaczaOpakowanaWBladRozproszonyWskazujeSiec() {
        SQLException blad = new SQLException("""
                ORA-02055: distributed update operation failed; rollback required
                ORA-02068: following severe error from AMEX
                ORA-03113: end-of-file on communication channel""", "42000", 2055);
        BladApi wynik = (BladApi) BledyBazy.przetlumacz(opakuj(blad), "VISA");
        assertThat(wynik.kod()).isEqualTo("91");
        assertThat(wynik.szczegoly()).containsEntry("siec", "AMEX");
    }

    @Test
    void niedostepnoscZgloszonaPrzezPakietWskazujeSiec() {
        SQLException blad = new SQLException("""
                ORA-02055: distributed update operation failed; rollback required
                ORA-20091: Siec niedostepna: AMEX
                ORA-12262: Cannot connect to database. Could not resolve hostname
                ORA-06512: at "BRAMKA.PKG_KLIENCI", line 40""", "42000", 2055);
        BladApi wynik = (BladApi) BledyBazy.przetlumacz(opakuj(blad), "VISA");
        assertThat(wynik.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(wynik.szczegoly()).containsEntry("siec", "AMEX");
    }

    @Test
    void replikacjaWTokuMaNaglowekRetryAfter() {
        SQLException blad = new SQLException("ORA-20020: Dane klienta w trakcie replikacji", "72000", 20020);
        BladApi wynik = (BladApi) BledyBazy.przetlumacz(opakuj(blad), "MC");
        assertThat(wynik.kod()).isEqualTo("REPLIKACJA_W_TOKU");
        assertThat(wynik.ponowZaSekund()).isEqualTo(30);
    }

    @Test
    void naruszenieUnikalnosciToDuplikat() {
        SQLException blad = new SQLException("ORA-00001: unique constraint violated", "23000", 1);
        BladApi wynik = (BladApi) BledyBazy.przetlumacz(opakuj(blad), "VISA");
        assertThat(wynik.kod()).isEqualTo("DUPLIKAT");
    }

    @Test
    void nieznanyBladPozostajeBezZmian() {
        RuntimeException blad = opakuj(new SQLException("ORA-00942: table or view does not exist", "42000", 942));
        assertThat(BledyBazy.przetlumacz(blad, "VISA")).isSameAs(blad);
    }
}
