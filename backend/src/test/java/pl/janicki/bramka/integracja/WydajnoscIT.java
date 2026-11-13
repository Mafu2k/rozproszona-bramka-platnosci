package pl.janicki.bramka.integracja;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WydajnoscIT {

    private static final int TRANSAKCJI_W_WEZLE = 10_000;

    private static final String DOPELNIJ_DO_10000 = """
            INSERT INTO transakcja (id_karty, id_sklepu, id_zadania, rrn, typ, kwota, waluta, status, kod_odpowiedzi, data_transakcji)
            SELECT ?, ?, 'obc-' || ? || '-' || LEVEL,
                   TO_CHAR(SYS_EXTRACT_UTC(SYSTIMESTAMP), 'YDDD') || '2' || LPAD(rrn_seq.NEXTVAL, 7, '0'),
                   'AUTORYZACJA', 1, 'PLN', 'ODRZUCONA', '51', SYS_EXTRACT_UTC(SYSTIMESTAMP) - NUMTODSINTERVAL(LEVEL, 'MINUTE')
              FROM dual
            CONNECT BY LEVEL <= ?
            """;

    @Test
    @DisplayName("11 / NFR1 (6.1): autoryzacja < 500 ms przy 10 000 transakcji w węźle, raport globalny < 3 s")
    void czasAutoryzacjiIRaportu() {
        Dane.czekajNaBramke();
        Dane.Sklep sklep = Dane.nowySklep();
        Dane.Karta karta = Dane.nowaKarta("MC", Dane.nowyKlient(), 10_000_000);
        assertThat(Dane.autoryzuj(sklep, karta.numer(), Dane.unikalny("rozgrzewka"), 1).status()).isEqualTo(200);

        long brakuje = TRANSAKCJI_W_WEZLE - Bazy.liczba("MC", "SELECT COUNT(*) FROM transakcja");
        if (brakuje > 0) {
            Bazy.wykonaj("MC", DOPELNIJ_DO_10000, karta.id(), sklep.id(), System.currentTimeMillis(), brakuje);
        }
        long wWezle = Bazy.liczba("MC", "SELECT COUNT(*) FROM transakcja");
        assertThat(wWezle).isGreaterThanOrEqualTo(TRANSAKCJI_W_WEZLE);

        List<Long> czasy = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            long start = System.nanoTime();
            Api.Odpowiedz odpowiedz = Dane.autoryzuj(sklep, karta.numer(), Dane.unikalny("wyd"), 100);
            czasy.add((System.nanoTime() - start) / 1_000_000);
            assertThat(odpowiedz.tekst("kod")).isEqualTo("00");
        }
        czasy.sort(null);
        long p95 = czasy.get(47);
        long maks = czasy.getLast();
        System.out.printf("NFR1: %d transakcji w węźle MC, autoryzacja mediana %d ms, p95 %d ms, max %d ms%n",
                wWezle, czasy.get(25), p95, maks);
        assertThat(maks).isLessThan(500);

        long start = System.nanoTime();
        Api.Odpowiedz statystyki = Api.get("/raporty/statystyki-sieci?od=2020-01-01T00:00:00Z&do=2030-01-01T00:00:00Z")
                .token(Dane.admin()).wyslij();
        long czasStatystyk = (System.nanoTime() - start) / 1_000_000;
        start = System.nanoTime();
        Api.Odpowiedz obroty = Api.get("/raporty/obroty-sklepu?idSklepu=" + sklep.id() + "&od=2020-01-01T00:00:00Z&do=2030-01-01T00:00:00Z")
                .token(Dane.admin()).wyslij();
        long czasObrotow = (System.nanoTime() - start) / 1_000_000;
        System.out.printf("NFR1: statystyki sieci %d ms, obroty sklepu %d ms%n", czasStatystyk, czasObrotow);
        assertThat(statystyki.status()).isEqualTo(200);
        assertThat(obroty.status()).isEqualTo(200);
        assertThat(czasStatystyk).isLessThan(3000);
        assertThat(czasObrotow).isLessThan(3000);
    }
}
