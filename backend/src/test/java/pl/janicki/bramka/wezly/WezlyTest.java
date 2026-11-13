package pl.janicki.bramka.wezly;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import pl.janicki.bramka.konfiguracja.BramkaProperties;

class WezlyTest {

    private static Wezly wezly;

    @BeforeAll
    static void utworz() {
        BramkaProperties properties = new BramkaProperties(
                new BramkaProperties.Baza("bramka", "haslo", 2, Duration.ofSeconds(1), Duration.ofSeconds(1)),
                List.of(new BramkaProperties.WezelProperties(1, "VISA", "jdbc:oracle:thin:@//localhost:59991/BRAK"),
                        new BramkaProperties.WezelProperties(2, "MC", "jdbc:oracle:thin:@//localhost:59992/BRAK"),
                        new BramkaProperties.WezelProperties(3, "AMEX", "jdbc:oracle:thin:@//localhost:59993/BRAK")),
                null, null, null, null, false);
        wezly = new Wezly(properties);
    }

    @AfterAll
    static void zamknij() {
        wezly.destroy();
    }

    @Test
    void identyfikatorWskazujeWezelPoReszcieZDzieleniaPrzez10() {
        assertThat(wezly.wezelDlaIdentyfikatora(1)).map(Wezel::kodSieci).contains("VISA");
        assertThat(wezly.wezelDlaIdentyfikatora(22)).map(Wezel::kodSieci).contains("MC");
        assertThat(wezly.wezelDlaIdentyfikatora(10_003)).map(Wezel::kodSieci).contains("AMEX");
        assertThat(wezly.wezelDlaIdentyfikatora(14)).isEmpty();
    }

    @Test
    void rrnWskazujeWezelPiataCyfra() {
        assertThat(wezly.wezelDlaRrn("631720000001")).map(Wezel::kodSieci).contains("MC");
        assertThat(wezly.wezelDlaRrn("631730000001")).map(Wezel::kodSieci).contains("AMEX");
        assertThat(wezly.wezelDlaRrn("631790000001")).isEmpty();
        assertThat(wezly.wezelDlaRrn("12345")).isEmpty();
    }

    @Test
    void zapytanieRozproszoneOznaczaNiedostepneWezlyJakoBrakujace() {
        WynikZWezlow<Integer> wynik = wezly.zapytajWszystkie(w -> w.jdbc().sql("SELECT 1 FROM dual").query(Integer.class).single());
        assertThat(wynik.wyniki()).isEmpty();
        assertThat(wynik.brakujaceSieci()).containsExactly("VISA", "MC", "AMEX");
        assertThat(wynik.czesciowy()).isTrue();
    }
}
