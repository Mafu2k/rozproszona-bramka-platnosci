package pl.janicki.bramka.integracja;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Types;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SkalowalnoscIT {

    private static final String KONTENER = "discover-db";
    private static final String STRUKTURA_TABEL = """
            SELECT c.table_name || '.' || c.column_name || ':' || c.data_type || '(' || c.data_length || ')' || c.nullable
              FROM user_tab_columns c
              JOIN user_tables t ON t.table_name = c.table_name
             ORDER BY 1
            """;

    @Test
    @DisplayName("10 / NFR7 (6.5): czwarta sieć jako nowy węzeł bez zmian w istniejących tabelach")
    void nowaSiecBezZmianWIstniejacychTabelach() throws Exception {
        Dane.czekajNaBramke();
        List<List<List<Object>>> strukturaPrzed = new ArrayList<>();
        for (String siec : List.of("VISA", "MC", "AMEX")) {
            strukturaPrzed.add(Bazy.wiersze(siec, STRUKTURA_TABEL));
        }
        try {
            uruchomWezel();
            migruj();
            Bazy.wykonaj("VISA", "INSERT INTO siec_kartowa (id_sieci, kod, nazwa, nr_wezla) VALUES (4, 'DISC', 'Discover', 4)");
            Bazy.wykonaj("VISA", "INSERT INTO zakres_bin (id_sieci, bin_od, bin_do, dlugosc_pan) VALUES (4, 60110000, 60119999, 16)");

            Duration replikacja = Czekanie.az(Duration.ofSeconds(90), "nowa sieć widoczna w replice nowego węzła",
                    () -> Bazy.liczba("DISC", "SELECT COUNT(*) FROM zakres_bin WHERE id_sieci = 4") == 1);
            System.out.printf("NFR7: węzeł DISC dostał dane wspólne po %d s%n", replikacja.toSeconds());

            long idKlienta = Bazy.liczba("DISC", "SELECT MIN(id_klienta) FROM klient WHERE status = 'AKTYWNY'");
            long idSklepu = Bazy.liczba("DISC", "SELECT MIN(id_sklepu) FROM sklep WHERE status = 'AKTYWNY'");
            try (Connection disc = Bazy.polacz("DISC")) {
                disc.setAutoCommit(false);
                long idKarty;
                try (CallableStatement wydanie = disc.prepareCall("{call pkg_karty.wydaj(?, ?, ?, DATE '2030-12-31', 1000, 'PLN', ?)}")) {
                    wydanie.setLong(1, idKlienta);
                    wydanie.setString(2, "D".repeat(64));
                    wydanie.setString(3, "601100******0000");
                    wydanie.registerOutParameter(4, Types.NUMERIC);
                    wydanie.execute();
                    idKarty = wydanie.getLong(4);
                }
                disc.commit();
                assertThat(idKarty % 10).isEqualTo(4);
                try (CallableStatement autoryzacja = disc.prepareCall(
                        "{call pkg_autoryzacja.autoryzuj(?, ?, ?, 100, 'PLN', ?, ?, ?, ?, ?, ?, ?)}")) {
                    autoryzacja.setString(1, "D".repeat(64));
                    autoryzacja.setLong(2, idSklepu);
                    autoryzacja.setString(3, Dane.unikalny("disc"));
                    for (int i = 4; i <= 10; i++) {
                        autoryzacja.registerOutParameter(i, i == 8 ? Types.TIMESTAMP : (i == 4 || i == 10 ? Types.NUMERIC : Types.VARCHAR));
                    }
                    autoryzacja.execute();
                    assertThat(autoryzacja.getString(7)).isEqualTo("00");
                    assertThat(autoryzacja.getString(5)).matches("\\d{4}4\\d{7}");
                }
            }

            for (int i = 0; i < 3; i++) {
                assertThat(Bazy.wiersze(List.of("VISA", "MC", "AMEX").get(i), STRUKTURA_TABEL))
                        .as("struktura tabel węzła %s", List.of("VISA", "MC", "AMEX").get(i))
                        .isEqualTo(strukturaPrzed.get(i));
            }
        } finally {
            posprzataj();
        }
    }

    private static void uruchomWezel() {
        Docker.wykonaj("rm", "-f", "-v", KONTENER);
        Docker.wykonaj("run", "-d", "--name", KONTENER,
                "--network", Srodowisko.SIEC_DOCKERA,
                "-p", "127.0.0.1:1524:1521",
                "-e", "ORACLE_PASSWORD=" + Dane.unikalny("P"),
                "-e", "BRAMKA_PASSWORD=" + Srodowisko.zmienna("BRAMKA_PASSWORD"),
                "-e", "LINK_PASSWORD=" + Srodowisko.zmienna("LINK_PASSWORD"),
                "-v", Srodowisko.sciezkaDlaDockera(Srodowisko.KATALOG_PROJEKTU.resolve("database").resolve("init"))
                        + ":/container-entrypoint-initdb.d:ro",
                "gvenzl/oracle-free:23.26.3");
        Docker.czekajNaBaze("DISC");
    }

    private static void migruj() {
        Docker.wykonaj("run", "--rm", "--network", Srodowisko.SIEC_DOCKERA,
                "-v", Srodowisko.sciezkaDlaDockera(Srodowisko.KATALOG_PROJEKTU.resolve("database")) + ":/flyway/projekt:ro",
                "-e", "FLYWAY_URL=jdbc:oracle:thin:@//" + KONTENER + ":1521/FREEPDB1",
                "-e", "FLYWAY_USER=bramka",
                "-e", "FLYWAY_PASSWORD=" + Srodowisko.zmienna("BRAMKA_PASSWORD"),
                "-e", "FLYWAY_CONNECT_RETRIES=20",
                "-e", "FLYWAY_LOCATIONS=filesystem:/flyway/projekt/migrations/repliki,filesystem:/flyway/projekt/migrations/fragmenty",
                "-e", "FLYWAY_PLACEHOLDERS_NR_WEZLA=4",
                "-e", "FLYWAY_PLACEHOLDERS_LINK_HASLO=" + Srodowisko.zmienna("LINK_PASSWORD"),
                "-e", "FLYWAY_PLACEHOLDERS_ADRES_VISA=//visa-db:1521/FREEPDB1",
                "-e", "FLYWAY_PLACEHOLDERS_ADRES_MC=//mastercard-db:1521/FREEPDB1",
                "-e", "FLYWAY_PLACEHOLDERS_ADRES_AMEX=//amex-db:1521/FREEPDB1",
                "flyway/flyway:13.12.2", "migrate");
    }

    private static void posprzataj() {
        try {
            for (String widok : List.of("SIEC_KARTOWA", "ZAKRES_BIN", "KLIENT", "SKLEP", "UZYTKOWNIK")) {
                Bazy.wykonaj("DISC", "DROP MATERIALIZED VIEW IF EXISTS " + widok);
            }
        } catch (RuntimeException e) {
            System.out.println("Sprzątanie widoków węzła DISC: " + e.getMessage());
        }
        Bazy.wykonaj("VISA", "DELETE FROM zakres_bin WHERE id_sieci = 4");
        Bazy.wykonaj("VISA", "DELETE FROM siec_kartowa WHERE id_sieci = 4");
        Docker.wykonaj("rm", "-f", "-v", KONTENER);
    }
}
