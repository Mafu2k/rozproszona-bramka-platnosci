package pl.janicki.bramka.uwierzytelnianie;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import pl.janicki.bramka.konfiguracja.BramkaProperties;
import pl.janicki.bramka.wezly.Wezel;
import pl.janicki.bramka.wezly.Wezly;

@Component
public class UzytkownicyStartowi {

    private static final Logger log = LoggerFactory.getLogger(UzytkownicyStartowi.class);

    private record Konto(String login, String rola, Integer idSieci, String haslo) {
    }

    private final Wezly wezly;
    private final PasswordEncoder hasla;
    private final BramkaProperties properties;
    private volatile boolean gotowe;

    public UzytkownicyStartowi(Wezly wezly, PasswordEncoder hasla, BramkaProperties properties) {
        this.wezly = wezly;
        this.hasla = hasla;
        this.properties = properties;
    }

    @Scheduled(initialDelayString = "PT3S", fixedDelayString = "PT30S")
    public void utworzBrakujace() {
        if (gotowe) {
            return;
        }
        List<Konto> konta = konta();
        if (konta.isEmpty()) {
            gotowe = true;
            return;
        }
        Wezel glowny = wezly.glowny();
        try {
            int utworzone = 0;
            for (Konto konto : konta) {
                boolean istnieje = glowny.jdbc().sql("SELECT COUNT(*) FROM uzytkownik WHERE login = ?")
                        .param(konto.login()).query(Long.class).single() > 0;
                if (!istnieje) {
                    glowny.jdbc().sql("INSERT INTO uzytkownik (login, hash_hasla, rola, id_sieci) VALUES (?, ?, ?, ?)")
                            .params(konto.login(), hasla.encode(konto.haslo()), konto.rola(), konto.idSieci())
                            .update();
                    utworzone++;
                }
            }
            gotowe = true;
            if (utworzone > 0) {
                log.info("Utworzono {} kont startowych panelu", utworzone);
            }
        } catch (RuntimeException e) {
            log.warn("Nie udało się utworzyć kont startowych (węzeł Visa niedostępny?), ponowię za 30 s: {}", e.getMessage());
        }
    }

    private List<Konto> konta() {
        BramkaProperties.UzytkownicyStartowi startowi = properties.uzytkownicyStartowi();
        List<Konto> konta = new ArrayList<>();
        if (startowi != null && niepuste(startowi.hasloAdmina())) {
            konta.add(new Konto("admin", ZalogowanyUzytkownik.ADMIN, null, startowi.hasloAdmina()));
        }
        if (startowi != null && niepuste(startowi.hasloOperatorow())) {
            for (Wezel wezel : wezly.wszystkie()) {
                konta.add(new Konto("operator." + nazwaSieci(wezel.kodSieci()), ZalogowanyUzytkownik.OPERATOR, wezel.nr(),
                        startowi.hasloOperatorow()));
            }
        }
        return konta;
    }

    private static String nazwaSieci(String kod) {
        return switch (kod) {
            case "MC" -> "mastercard";
            default -> kod.toLowerCase();
        };
    }

    private static boolean niepuste(String wartosc) {
        return wartosc != null && !wartosc.isBlank();
    }
}
