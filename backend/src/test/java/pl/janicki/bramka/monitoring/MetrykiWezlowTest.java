package pl.janicki.bramka.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import pl.janicki.bramka.monitoring.StanWezlowService.LogMv;
import pl.janicki.bramka.monitoring.StanWezlowService.StanReplikacji;
import pl.janicki.bramka.monitoring.StanWezlowService.StanWezla;
import pl.janicki.bramka.monitoring.StanWezlowService.StanWezlow;

class MetrykiWezlowTest {

    private final SimpleMeterRegistry rejestr = new SimpleMeterRegistry();
    private final StanWezlowService stanWezlow = mock(StanWezlowService.class);
    private final MetrykiWezlow metryki = new MetrykiWezlow(stanWezlow, rejestr);

    private double wartosc(String nazwa, String etykieta, String wartoscEtykiety) {
        return rejestr.find(nazwa).tag(etykieta, wartoscEtykiety).gauge().value();
    }

    @Test
    void stanWezlowJestWidocznyJakoMierniki() {
        when(stanWezlow.stan()).thenReturn(new StanWezlow(OffsetDateTime.now(), List.of(
                new StanWezla("VISA", 1, "GLOWNY", true, 3L, null, List.of(new LogMv("KLIENT", 4, 65536)), 0, null),
                new StanWezla("MC", 2, "REPLIKA", true, 4L, new StanReplikacji(OffsetDateTime.now(), 12L, false, null), null, 1, null),
                new StanWezla("AMEX", 3, "REPLIKA", false, null, null, null, null, "Węzeł niedostępny"))));

        metryki.odswiez();

        assertThat(wartosc("bramka.wezel.dostepny", "siec", "VISA")).isEqualTo(1);
        assertThat(wartosc("bramka.wezel.dostepny", "siec", "AMEX")).isZero();
        assertThat(wartosc("bramka.replikacja.opoznienie.sekundy", "siec", "MC")).isEqualTo(12);
        assertThat(wartosc("bramka.replikacja.opoznienie.sekundy", "siec", "AMEX")).isNaN();
        assertThat(wartosc("bramka.transakcje.2pc.wiszace", "siec", "MC")).isEqualTo(1);
        assertThat(wartosc("bramka.log.mv.wiersze", "tabela", "KLIENT")).isEqualTo(4);
    }

    @Test
    void kolejnyPomiarAktualizujeTeSameMierniki() {
        when(stanWezlow.stan())
                .thenReturn(new StanWezlow(OffsetDateTime.now(),
                        List.of(new StanWezla("MC", 2, "REPLIKA", true, 4L, null, null, 0, null))))
                .thenReturn(new StanWezlow(OffsetDateTime.now(),
                        List.of(new StanWezla("MC", 2, "REPLIKA", false, null, null, null, null, "Węzeł niedostępny"))));

        metryki.odswiez();
        metryki.odswiez();

        assertThat(rejestr.find("bramka.wezel.dostepny").gauges()).hasSize(1);
        assertThat(wartosc("bramka.wezel.dostepny", "siec", "MC")).isZero();
    }
}
