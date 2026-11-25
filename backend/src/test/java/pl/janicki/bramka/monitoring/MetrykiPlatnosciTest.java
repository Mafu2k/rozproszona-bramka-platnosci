package pl.janicki.bramka.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import pl.janicki.bramka.bledy.BladApi;
import pl.janicki.bramka.platnosci.WynikPlatnosci;

class MetrykiPlatnosciTest {

    private SimpleMeterRegistry rejestr;
    private MetrykiPlatnosci metryki;

    @BeforeEach
    void przygotuj() {
        rejestr = new SimpleMeterRegistry();
        metryki = new MetrykiPlatnosci(rejestr);
    }

    private double licznik(String siec, String typ, String kod, boolean powtorzona) {
        var miernik = rejestr.find("bramka.platnosci")
                .tags("siec", siec, "typ", typ, "kod", kod, "powtorzona", String.valueOf(powtorzona))
                .counter();
        return miernik == null ? 0 : miernik.count();
    }

    private static WynikPlatnosci wynik(String siec, String kod, boolean powtorzona) {
        return new WynikPlatnosci("z1", WynikPlatnosci.decyzja(kod), kod, null, "632100000001", "AUTORYZACJA", 100, "PLN", siec,
                null, null, null, null, powtorzona);
    }

    @Test
    void decyzjaTrafiaDoLicznikaICzasuSwojejSieci() {
        metryki.zarejestruj(wynik("MC", "00", false), System.nanoTime());
        metryki.zarejestruj(wynik("MC", "51", false), System.nanoTime());
        metryki.zarejestruj(wynik("MC", "00", false), System.nanoTime());
        metryki.zarejestruj(wynik("MC", "00", true), System.nanoTime());

        assertThat(licznik("MC", "AUTORYZACJA", "00", false)).isEqualTo(2);
        assertThat(licznik("MC", "AUTORYZACJA", "51", false)).isEqualTo(1);
        assertThat(licznik("MC", "AUTORYZACJA", "00", true)).isEqualTo(1);
        assertThat(rejestr.find("bramka.platnosci.czas").tags("siec", "MC", "typ", "AUTORYZACJA").timer().count()).isEqualTo(4);
    }

    @Test
    void niedostepnaSiecJestPrzypisanaDoSieciZBledu() {
        metryki.zarejestrujBlad(null, "AUTORYZACJA", BladApi.siecNiedostepna("VISA"), System.nanoTime());

        assertThat(licznik("VISA", "AUTORYZACJA", "91", false)).isEqualTo(1);
    }

    @Test
    void bladPrzedRoutingiemINieoczekiwanyBladMajaWlasneEtykiety() {
        metryki.zarejestrujBlad(null, "AUTORYZACJA", BladApi.nieprawidlowyNumerKarty(), System.nanoTime());
        metryki.zarejestrujBlad("AMEX", "ZWROT", new IllegalStateException("awaria"), System.nanoTime());

        assertThat(licznik(MetrykiPlatnosci.BRAK_SIECI, "AUTORYZACJA", "14", false)).isEqualTo(1);
        assertThat(licznik("AMEX", "ZWROT", "96", false)).isEqualTo(1);
    }
}
