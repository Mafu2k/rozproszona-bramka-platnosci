package pl.janicki.bramka.integracja;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BlokadaGlobalnaIT {

    @Test
    @DisplayName("FR7: blokada klienta we wszystkich sieciach blokuje karty na trzech węzłach")
    void blokadaWeWszystkichSieciach() {
        Dane.czekajNaBramke();
        Dane.Sklep sklep = Dane.nowySklep();
        long klient = Dane.nowyKlient();
        Dane.Karta visa = Dane.nowaKarta("VISA", klient, 50_000);
        Dane.Karta mc = Dane.nowaKarta("MC", klient, 50_000);
        Dane.Karta amex = Dane.nowaKarta("AMEX", klient, 50_000);

        Api.Odpowiedz blokada = Api.post("/klienci/" + klient + "/blokada-globalna").token(Dane.admin())
                .idempotencja(Dane.unikalny("blok")).wyslij();

        assertThat(blokada.status()).isEqualTo(200);
        assertThat(blokada.tresc().get("zablokowaneKarty").get("VISA").asInt()).isEqualTo(1);
        assertThat(blokada.tresc().get("zablokowaneKarty").get("MC").asInt()).isEqualTo(1);
        assertThat(blokada.tresc().get("zablokowaneKarty").get("AMEX").asInt()).isEqualTo(1);
        for (Dane.Karta karta : List.of(visa, mc, amex)) {
            Api.Odpowiedz stan = Api.get("/karty/" + karta.id()).token(Dane.operator(karta.siec())).wyslij();
            assertThat(stan.tekst("status")).isEqualTo("ZABLOKOWANA");
        }
        Api.Odpowiedz platnosc = Dane.autoryzuj(sklep, mc.numer(), Dane.unikalny("po-blokadzie"), 100);
        assertThat(platnosc.tekst("kod")).isEqualTo("62");
        Api.Odpowiedz odblokowanie = Api.post("/karty/" + mc.id() + "/odblokowanie").token(Dane.operator("MC"))
                .idempotencja(Dane.unikalny("odblok")).wyslij();
        assertThat(odblokowanie.status()).isEqualTo(409);
        assertThat(odblokowanie.tekst("kod")).isEqualTo("KLIENT_NIEAKTYWNY");
    }

    @Test
    @DisplayName("9a: FR7 przy niedostępnym węźle AmEx - wycofanie na wszystkich węzłach")
    void blokadaPrzyNiedostepnymWezleNicNieZmienia() {
        Dane.czekajNaBramke();
        long klient = Dane.nowyKlient();
        Dane.Karta mc = Dane.nowaKarta("MC", klient, 50_000);
        Dane.nowaKarta("AMEX", klient, 50_000);
        String tokenAdmina = Dane.admin();

        Docker.zatrzymaj("amex-db");
        try {
            Api.Odpowiedz blokada = Api.post("/klienci/" + klient + "/blokada-globalna").token(tokenAdmina)
                    .idempotencja(Dane.unikalny("blok")).wyslij();
            assertThat(blokada.status()).isEqualTo(503);
            assertThat(blokada.tekst("kod")).isEqualTo("91");
            assertThat(blokada.tresc().get("szczegoly").get("siec").asString()).isEqualTo("AMEX");
            assertThat(Bazy.wartosc("VISA", "SELECT status FROM klient WHERE id_klienta = ?", klient)).isEqualTo("AKTYWNY");
            assertThat(Bazy.wartosc("MC", "SELECT status FROM karta WHERE id_karty = ?", mc.id())).isEqualTo("AKTYWNA");
        } finally {
            Docker.uruchom("amex-db", "AMEX");
        }
        Dane.czekajNaBramke();
    }

    @Test
    @DisplayName("9b: przerwanie w trakcie 2PC - transakcja w DBA_2PC_PENDING rozstrzygnięta automatycznie, stan spójny")
    void przerwanieDwufazowegoZatwierdzaniaRozstrzyganeAutomatycznie() throws Exception {
        Dane.czekajNaBramke();
        long klient = Dane.nowyKlient();
        Dane.Karta mc = Dane.nowaKarta("MC", klient, 50_000);
        Dane.Karta amex = Dane.nowaKarta("AMEX", klient, 50_000);

        nadajUprawnienieSymulacji(true);
        try (Connection visa = Bazy.polacz("VISA")) {
            visa.setAutoCommit(false);
            try (CallableStatement blokada = visa.prepareCall("{call pkg_klienci.zablokuj_we_wszystkich_sieciach(?, ?, ?, ?)}")) {
                blokada.setLong(1, klient);
                blokada.registerOutParameter(2, Types.NUMERIC);
                blokada.registerOutParameter(3, Types.NUMERIC);
                blokada.registerOutParameter(4, Types.NUMERIC);
                blokada.execute();
            }
            try (Statement zatwierdzenie = visa.createStatement()) {
                assertThatThrownBy(() -> zatwierdzenie.execute("COMMIT COMMENT 'ORA-2PC-CRASH-TEST-4'"))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("ORA-02054");
            }
        } finally {
            nadajUprawnienieSymulacji(false);
        }

        Duration rozstrzygniecie = Czekanie.az(Duration.ofMinutes(3), "brak wiszących transakcji 2PC",
                () -> List.of("VISA", "MC", "AMEX").stream()
                        .allMatch(siec -> Bazy.liczba(siec, "SELECT COUNT(*) FROM sys.dba_2pc_pending") == 0));
        System.out.printf("Test 9b: transakcje w toku rozstrzygnięte po %d ms%n", rozstrzygniecie.toMillis());
        String statusKlienta = (String) Bazy.wartosc("VISA", "SELECT status FROM klient WHERE id_klienta = ?", klient);
        String statusMc = (String) Bazy.wartosc("MC", "SELECT status FROM karta WHERE id_karty = ?", mc.id());
        String statusAmex = (String) Bazy.wartosc("AMEX", "SELECT status FROM karta WHERE id_karty = ?", amex.id());
        boolean wszedzieZablokowane = "ZABLOKOWANY".equals(statusKlienta) && "ZABLOKOWANA".equals(statusMc) && "ZABLOKOWANA".equals(statusAmex);
        boolean nigdzieNieZablokowane = "AKTYWNY".equals(statusKlienta) && "AKTYWNA".equals(statusMc) && "AKTYWNA".equals(statusAmex);
        assertThat(wszedzieZablokowane || nigdzieNieZablokowane)
                .as("stan po rozstrzygnięciu: klient=%s MC=%s AMEX=%s", statusKlienta, statusMc, statusAmex)
                .isTrue();
    }

    private static void nadajUprawnienieSymulacji(boolean nadaj) {
        for (String kontener : List.of("visa-db", "mastercard-db", "amex-db")) {
            Docker.sqlJakoSys(kontener, nadaj
                    ? "GRANT FORCE ANY TRANSACTION TO bramka;\nGRANT FORCE ANY TRANSACTION TO wezel_link;"
                    : "REVOKE FORCE ANY TRANSACTION FROM bramka;\nREVOKE FORCE ANY TRANSACTION FROM wezel_link;");
        }
    }
}
