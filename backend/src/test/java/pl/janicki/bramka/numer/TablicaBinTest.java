package pl.janicki.bramka.numer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class TablicaBinTest {

    private TablicaBin tablica;

    @BeforeEach
    void przygotuj() {
        tablica = new TablicaBin(null);
        tablica.ustaw(List.of(
                new ZakresBin(40000000, 49999999, 16, "VISA", 1),
                new ZakresBin(51000000, 55999999, 16, "MC", 2),
                new ZakresBin(22210000, 27209999, 16, "MC", 2),
                new ZakresBin(34000000, 34999999, 15, "AMEX", 3),
                new ZakresBin(37000000, 37999999, 15, "AMEX", 3)));
    }

    @Test
    void rozpoznajeSiecPoBin() {
        assertThat(tablica.rozpoznaj(NumerKarty.z("4111111111111111"))).map(ZakresBin::kodSieci).contains("VISA");
        assertThat(tablica.rozpoznaj(NumerKarty.z("5555555555554444"))).map(ZakresBin::kodSieci).contains("MC");
        assertThat(tablica.rozpoznaj(NumerKarty.z("2223003122003222"))).map(ZakresBin::kodSieci).contains("MC");
        assertThat(tablica.rozpoznaj(NumerKarty.z("378282246310005"))).map(ZakresBin::nrWezla).contains(3);
    }

    @Test
    void nieRozpoznajeNumeruSpozaZakresowAniOZlejDlugosci() {
        assertThat(tablica.rozpoznaj(NumerKarty.z("6011111111111117"))).isEmpty();
        assertThat(tablica.rozpoznaj(NumerKarty.z("4222222222222"))).isEmpty();
    }

    @RepeatedTest(50)
    void generatorTworzyPoprawneNumeryWZakresieSieci() {
        GeneratorNumeruKarty generator = new GeneratorNumeruKarty(tablica, Map.of());
        for (String siec : List.of("VISA", "MC", "AMEX")) {
            NumerKarty numer = generator.nowyNumer(siec);
            assertThat(Luhn.poprawny(numer.cyfry())).isTrue();
            assertThat(tablica.rozpoznaj(numer)).map(ZakresBin::kodSieci).contains(siec);
        }
    }

    @RepeatedTest(20)
    void generatorUzywaBinuWydawcy() {
        GeneratorNumeruKarty generator = new GeneratorNumeruKarty(tablica,
                Map.of("VISA", "45391000", "MC", "53999100", "AMEX", "37971200"));
        NumerKarty visa = generator.nowyNumer("VISA");
        NumerKarty mc = generator.nowyNumer("MC");
        NumerKarty amex = generator.nowyNumer("AMEX");
        assertThat(visa.cyfry()).startsWith("45391000").hasSize(16);
        assertThat(mc.cyfry()).startsWith("53999100").hasSize(16);
        assertThat(amex.cyfry()).startsWith("37971200").hasSize(15);
        assertThat(List.of(visa, mc, amex)).allSatisfy(n -> assertThat(Luhn.poprawny(n.cyfry())).isTrue());
        assertThat(tablica.rozpoznaj(amex)).map(ZakresBin::kodSieci).contains("AMEX");
    }

    @Test
    void binWydawcySpozaZakresowSieciToBladKonfiguracji() {
        GeneratorNumeruKarty generator = new GeneratorNumeruKarty(tablica, Map.of("MC", "45391000"));
        assertThatThrownBy(() -> generator.nowyNumer("MC"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nie należy do zakresów sieci MC");
    }
}
