package pl.janicki.bramka.demo;

import java.sql.CallableStatement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import pl.janicki.bramka.konfiguracja.BramkaProperties;
import pl.janicki.bramka.numer.NumerKarty;
import pl.janicki.bramka.numer.TokenKarty;
import pl.janicki.bramka.platnosci.PlatnosciService;
import pl.janicki.bramka.platnosci.ZadanieAutoryzacji;
import pl.janicki.bramka.wezly.Wezel;
import pl.janicki.bramka.wezly.Wezly;
import pl.janicki.bramka.wspolne.Kwoty;

@Component
public class DaneDemo {

    private static final Logger log = LoggerFactory.getLogger(DaneDemo.class);
    private static final long LIMIT_DEMO = 500_000;

    private record KartaDemo(String siec, String numer, String emailKlienta) {
    }

    private static final List<KartaDemo> KARTY = List.of(
            new KartaDemo("VISA", "4111111111111111", "anna.kowalska@example.com"),
            new KartaDemo("VISA", "4012888888881881", "piotr.nowak@example.com"),
            new KartaDemo("MC", "5555555555554444", "anna.kowalska@example.com"),
            new KartaDemo("MC", "5105105105105100", "katarzyna.wisniewska@example.com"),
            new KartaDemo("MC", "2223003122003222", "tomasz.wojcik@example.com"),
            new KartaDemo("AMEX", "378282246310005", "anna.kowalska@example.com"),
            new KartaDemo("AMEX", "371449635398431", "magdalena.kaminska@example.com"));

    private static final List<String> NIP_SKLEPOW = List.of("5260181596", "1860913901", "6281948218");

    private final Wezly wezly;
    private final TokenKarty tokenKarty;
    private final PlatnosciService platnosci;
    private final boolean wlaczone;
    private volatile boolean gotowe;

    public DaneDemo(Wezly wezly, TokenKarty tokenKarty, PlatnosciService platnosci, BramkaProperties properties) {
        this.wezly = wezly;
        this.tokenKarty = tokenKarty;
        this.platnosci = platnosci;
        this.wlaczone = properties.daneDemo();
    }

    @Scheduled(initialDelayString = "PT10S", fixedDelayString = "PT30S")
    public void przygotuj() {
        if (!wlaczone || gotowe) {
            return;
        }
        try {
            List<Long> sklepy = NIP_SKLEPOW.stream().map(this::idSklepu).flatMap(Optional::stream).toList();
            int wydane = 0;
            for (KartaDemo karta : KARTY) {
                if (wydajJesliBrak(karta)) {
                    wydane++;
                }
            }
            int numer = 0;
            for (KartaDemo karta : KARTY) {
                for (int i = 0; i < sklepy.size(); i++) {
                    numer++;
                    long kwota = 1_999 + (numer * 7_331L) % 45_000;
                    platnosci.autoryzuj(sklepy.get(i), new ZadanieAutoryzacji(
                            "demo-" + karta.numer().substring(karta.numer().length() - 4) + "-" + i, karta.numer(), kwota, "PLN"));
                }
            }
            gotowe = true;
            log.info("Dane demonstracyjne gotowe ({} nowych kart testowych)", wydane);
        } catch (RuntimeException e) {
            log.info("Dane demonstracyjne jeszcze niegotowe ({}), ponowię za 30 s", e.getMessage());
        }
    }

    private boolean wydajJesliBrak(KartaDemo karta) {
        Wezel wezel = wezly.wezelSieci(karta.siec()).orElseThrow();
        NumerKarty numer = NumerKarty.z(karta.numer());
        String token = tokenKarty.token(numer);
        boolean istnieje = wezel.jdbc().sql("SELECT COUNT(*) FROM karta WHERE token = ?").param(token).query(Long.class).single() > 0;
        if (istnieje) {
            return false;
        }
        long idKlienta = wezly.czytajDaneWspolne(w -> w.jdbc().sql("SELECT id_klienta FROM klient WHERE email = ?")
                .param(karta.emailKlienta()).query(Long.class).single()).wartosc();
        LocalDate waznosc = YearMonth.now(ZoneOffset.UTC).plusMonths(36).atEndOfMonth();
        wezel.transakcja().executeWithoutResult(status -> wezel.jdbcTemplate().execute((ConnectionCallback<Long>) polaczenie -> {
            try (CallableStatement procedura = polaczenie.prepareCall("{call pkg_karty.wydaj(?, ?, ?, ?, ?, ?, ?)}")) {
                procedura.setLong(1, idKlienta);
                procedura.setString(2, token);
                procedura.setString(3, numer.maska());
                procedura.setObject(4, waznosc);
                procedura.setBigDecimal(5, Kwoty.zGroszy(LIMIT_DEMO));
                procedura.setString(6, "PLN");
                procedura.registerOutParameter(7, Types.NUMERIC);
                procedura.execute();
                return procedura.getLong(7);
            }
        }));
        return true;
    }

    private Optional<Long> idSklepu(String nip) {
        return wezly.czytajDaneWspolne(w -> w.jdbc().sql("SELECT id_sklepu FROM sklep WHERE nip = ?")
                .param(nip).query(Long.class).optional()).wartosc();
    }
}
