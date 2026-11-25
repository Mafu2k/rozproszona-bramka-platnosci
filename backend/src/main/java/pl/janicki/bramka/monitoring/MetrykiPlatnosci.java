package pl.janicki.bramka.monitoring;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import pl.janicki.bramka.bledy.BladApi;
import pl.janicki.bramka.platnosci.WynikPlatnosci;

@Component
public class MetrykiPlatnosci {

    public static final String BRAK_SIECI = "BRAK";
    private static final String BLAD_SYSTEMU = "96";

    private final MeterRegistry rejestr;

    public MetrykiPlatnosci(MeterRegistry rejestr) {
        this.rejestr = rejestr;
    }

    public void zarejestruj(WynikPlatnosci wynik, long poczatekNanos) {
        zapisz(wynik.siec(), wynik.typ(), wynik.kod(), wynik.powtorzona(), poczatekNanos);
    }

    public void zarejestrujBlad(String siec, String typ, RuntimeException blad, long poczatekNanos) {
        if (blad instanceof BladApi bladApi) {
            Object siecZBledu = bladApi.szczegoly() == null ? null : bladApi.szczegoly().get("siec");
            zapisz(siecZBledu != null ? siecZBledu.toString() : siec, typ, bladApi.kod(), false, poczatekNanos);
        } else {
            zapisz(siec, typ, BLAD_SYSTEMU, false, poczatekNanos);
        }
    }

    private void zapisz(String siec, String typ, String kod, boolean powtorzona, long poczatekNanos) {
        String etykietaSieci = siec == null ? BRAK_SIECI : siec;
        Counter.builder("bramka.platnosci")
                .description("Decyzje i błędy płatności według sieci, typu i kodu odpowiedzi")
                .tag("siec", etykietaSieci)
                .tag("typ", typ)
                .tag("kod", kod)
                .tag("powtorzona", String.valueOf(powtorzona))
                .register(rejestr)
                .increment();
        Timer.builder("bramka.platnosci.czas")
                .description("Czas obsługi płatności przez bramkę")
                .tag("siec", etykietaSieci)
                .tag("typ", typ)
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofMillis(1))
                .maximumExpectedValue(Duration.ofSeconds(10))
                .register(rejestr)
                .record(System.nanoTime() - poczatekNanos, TimeUnit.NANOSECONDS);
    }
}
