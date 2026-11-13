package pl.janicki.bramka.uwierzytelnianie;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import pl.janicki.bramka.bledy.BladApi;

@Component
public class LimitProbLogowania {

    private static final int MAKS_NIEUDANYCH = 5;
    private static final Duration OKNO = Duration.ofMinutes(5);
    private static final Duration BLOKADA = Duration.ofMinutes(5);

    private record Proby(int nieudane, Instant poczatekOkna, Instant zablokowaneDo) {
    }

    private final Map<String, Proby> proby = new ConcurrentHashMap<>();

    public void sprawdz(String login) {
        Proby stan = proby.get(klucz(login));
        Instant teraz = Instant.now();
        if (stan != null && stan.zablokowaneDo() != null && stan.zablokowaneDo().isAfter(teraz)) {
            long sekundy = Duration.between(teraz, stan.zablokowaneDo()).toSeconds() + 1;
            throw new BladApi(HttpStatus.TOO_MANY_REQUESTS, "ZBYT_WIELE_PROB", "Zbyt wiele nieudanych prób logowania", null, (int) sekundy);
        }
    }

    public void porazka(String login) {
        Instant teraz = Instant.now();
        proby.compute(klucz(login), (k, stan) -> {
            if (stan == null || stan.poczatekOkna().plus(OKNO).isBefore(teraz)) {
                return new Proby(1, teraz, null);
            }
            int nieudane = stan.nieudane() + 1;
            return new Proby(nieudane, stan.poczatekOkna(), nieudane >= MAKS_NIEUDANYCH ? teraz.plus(BLOKADA) : null);
        });
    }

    public void sukces(String login) {
        proby.remove(klucz(login));
    }

    private static String klucz(String login) {
        return login.trim().toLowerCase();
    }
}
