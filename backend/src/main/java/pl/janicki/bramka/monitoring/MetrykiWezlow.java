package pl.janicki.bramka.monitoring;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class MetrykiWezlow {

    private final StanWezlowService stanWezlow;
    private final MeterRegistry rejestr;
    private final Map<String, AtomicReference<Double>> wartosci = new ConcurrentHashMap<>();

    public MetrykiWezlow(StanWezlowService stanWezlow, MeterRegistry rejestr) {
        this.stanWezlow = stanWezlow;
        this.rejestr = rejestr;
    }

    @Scheduled(initialDelayString = "PT5S", fixedDelayString = "PT5S")
    public void odswiez() {
        for (StanWezlowService.StanWezla wezel : stanWezlow.stan().wezly()) {
            ustaw("bramka.wezel.dostepny", "siec", wezel.siec(), wezel.dostepny() ? 1 : 0);
            ustaw("bramka.transakcje.2pc.wiszace", "siec", wezel.siec(),
                    wezel.wiszaceTransakcje2pc() == null ? Double.NaN : wezel.wiszaceTransakcje2pc());
            if ("REPLIKA".equals(wezel.rola())) {
                Long opoznienie = wezel.replikacja() == null ? null : wezel.replikacja().opoznienieSekund();
                ustaw("bramka.replikacja.opoznienie.sekundy", "siec", wezel.siec(), opoznienie == null ? Double.NaN : opoznienie);
            }
            if (wezel.logiMv() != null) {
                wezel.logiMv().forEach(log -> ustaw("bramka.log.mv.wiersze", "tabela", log.tabela(), log.liczbaWierszy()));
            }
        }
    }

    private void ustaw(String nazwa, String etykieta, String wartoscEtykiety, double wartosc) {
        wartosci.computeIfAbsent(nazwa + "|" + wartoscEtykiety, klucz -> {
            AtomicReference<Double> miejsce = new AtomicReference<>(Double.NaN);
            Gauge.builder(nazwa, miejsce, AtomicReference::get).tag(etykieta, wartoscEtykiety).register(rejestr);
            return miejsce;
        }).set(wartosc);
    }
}
