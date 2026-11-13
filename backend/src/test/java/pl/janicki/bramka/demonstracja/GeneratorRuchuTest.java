package pl.janicki.bramka.demonstracja;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

class GeneratorRuchuTest {

    private static final Map<String, String> HASLA = Map.of("PANEL_ADMIN_PASSWORD", "a", "PANEL_OPERATOR_PASSWORD", "o");

    @Test
    void konfiguracjaMaRozsadneWartosciDomyslne() {
        Konfiguracja konfiguracja = Konfiguracja.zeSrodowiska(HASLA);

        assertThat(konfiguracja.adresBramki()).isEqualTo("http://localhost:8080");
        assertThat(konfiguracja.hostIso()).isEqualTo("localhost");
        assertThat(konfiguracja.portIso()).isEqualTo(8583);
        assertThat(konfiguracja.tps()).isEqualTo(20);
        assertThat(konfiguracja.udzialIso()).isEqualTo(0.2);
    }

    @Test
    void konfiguracjaOdrzucaBrakHaselIZleParametryRuchu() {
        assertThatThrownBy(() -> Konfiguracja.zeSrodowiska(Map.of())).hasMessageContaining("PANEL_ADMIN_PASSWORD");
        Map<String, String> zaDuzo = new java.util.HashMap<>(HASLA);
        zaDuzo.put("RUCH_TPS", "1000");
        assertThatThrownBy(() -> Konfiguracja.zeSrodowiska(zaDuzo)).hasMessageContaining("RUCH_TPS");
        Map<String, String> zlyUdzial = new java.util.HashMap<>(HASLA);
        zlyUdzial.put("RUCH_UDZIAL_ISO", "1.5");
        assertThatThrownBy(() -> Konfiguracja.zeSrodowiska(zlyUdzial)).hasMessageContaining("RUCH_UDZIAL_ISO");
    }

    @Test
    void kwotyWygladajaJakCenyIMieszczaSieWZakresie() {
        SplittableRandom losowanie = new SplittableRandom(7);
        for (int i = 0; i < 10_000; i++) {
            long kwota = Uczestnicy.losujKwote(losowanie, i % 2 == 0 ? "AMEX" : "VISA");
            assertThat(kwota).isBetween(100L, 1_500_099L);
            assertThat(kwota % 100).isIn(0L, 99L);
        }
    }

    @Test
    void podsumowanieOknaPokazujeRuchSieciIKody() {
        Statystyki statystyki = new Statystyki();
        statystyki.autoryzacja(Statystyki.Kanal.REST, "VISA", "00", 40_000_000, false);
        statystyki.autoryzacja(Statystyki.Kanal.ISO, "MC", "51", 60_000_000, false);
        statystyki.zwrot("00", 30_000_000);

        String linia = statystyki.podsumujOkno();

        assertThat(linia).contains("VISA", "MC", "AMEX", "00:2", "51:1", "zwroty 1");
        assertThat(statystyki.podsumujOkno()).contains("brak ruchu");
        assertThat(statystyki.podsumowanieCalosci()).contains("Wysłano 3");
    }
}
