package pl.janicki.bramka.uwierzytelnianie;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import pl.janicki.bramka.wezly.Wezly;

@Component
public class RejestrSklepow {

    private static final Duration WAZNOSC_WPISU = Duration.ofSeconds(30);
    private static final Duration WAZNOSC_BRAKU = Duration.ofSeconds(5);

    private record Wpis(Optional<SklepUwierzytelniony> sklep, Instant wygasa) {
    }

    private final Wezly wezly;
    private final Map<String, Wpis> pamiec = new ConcurrentHashMap<>();

    public RejestrSklepow(Wezly wezly) {
        this.wezly = wezly;
    }

    public Optional<SklepUwierzytelniony> poKluczu(String kluczApi) {
        String skrot = KluczApi.skrot(kluczApi);
        Wpis wpis = pamiec.get(skrot);
        if (wpis != null && wpis.wygasa().isAfter(Instant.now())) {
            return wpis.sklep();
        }
        Optional<SklepUwierzytelniony> sklep = wezly.czytajDaneWspolne(wezel -> wezel.jdbc()
                .sql("SELECT id_sklepu, nazwa, status FROM sklep WHERE hash_klucza_api = ?")
                .param(skrot)
                .query((rs, i) -> new SklepUwierzytelniony(rs.getLong(1), rs.getString(2), rs.getString(3)))
                .optional()).wartosc();
        pamiec.put(skrot, new Wpis(sklep, Instant.now().plus(sklep.isPresent() ? WAZNOSC_WPISU : WAZNOSC_BRAKU)));
        return sklep;
    }

    public Optional<SklepUwierzytelniony> poIdentyfikatorze(long idSklepu) {
        return wezly.czytajDaneWspolne(wezel -> wezel.jdbc()
                .sql("SELECT id_sklepu, nazwa, status FROM sklep WHERE id_sklepu = ?")
                .param(idSklepu)
                .query((rs, i) -> new SklepUwierzytelniony(rs.getLong(1), rs.getString(2), rs.getString(3)))
                .optional()).wartosc();
    }

    public void wyczysc() {
        pamiec.clear();
    }
}
