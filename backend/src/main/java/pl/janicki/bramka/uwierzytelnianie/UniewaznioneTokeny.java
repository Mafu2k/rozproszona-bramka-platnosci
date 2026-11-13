package pl.janicki.bramka.uwierzytelnianie;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class UniewaznioneTokeny {

    private final Map<String, Instant> uniewaznione = new ConcurrentHashMap<>();

    public void uniewaznij(String identyfikator, Instant wygasa) {
        if (identyfikator != null) {
            uniewaznione.put(identyfikator, wygasa == null ? Instant.now().plusSeconds(3600) : wygasa);
        }
    }

    public boolean uniewazniony(String identyfikator) {
        return identyfikator != null && uniewaznione.containsKey(identyfikator);
    }

    @Scheduled(fixedDelayString = "PT5M")
    public void usunWygasle() {
        Instant teraz = Instant.now();
        uniewaznione.values().removeIf(wygasa -> wygasa.isBefore(teraz));
    }
}
