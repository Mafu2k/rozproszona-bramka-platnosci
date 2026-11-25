package pl.janicki.bramka.monitoring;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonInclude;

import pl.janicki.bramka.wezly.Wezel;
import pl.janicki.bramka.wezly.Wezly;
import pl.janicki.bramka.wezly.WynikZWezlow;
import pl.janicki.bramka.wspolne.CzasUtc;

@Service
public class StanWezlowService {

    public static final Duration LIMIT_OPOZNIENIA_REPLIK = Duration.ofSeconds(60);
    private static final Duration WAZNOSC_POMIARU = Duration.ofSeconds(2);

    public record BladOdswiezania(OffsetDateTime czas, String komunikat) {
    }

    public record StanReplikacji(OffsetDateTime ostatnieOdswiezenie, Long opoznienieSekund, boolean przekroczonoLimit,
            BladOdswiezania ostatniBlad) {
    }

    public record LogMv(String tabela, long liczbaWierszy, long rozmiarBajtow) {
    }

    public record StanWezla(String siec, int nrWezla, String rola, boolean dostepny, Long czasOdpowiedziMs,
            @JsonInclude(JsonInclude.Include.NON_NULL) StanReplikacji replikacja, List<LogMv> logiMv, Integer wiszaceTransakcje2pc,
            String blad) {
    }

    public record StanWezlow(OffsetDateTime czasPomiaru, List<StanWezla> wezly) {
    }

    private final Wezly wezly;
    private volatile StanWezlow ostatniPomiar;
    private volatile Instant czasOstatniegoPomiaru = Instant.EPOCH;

    public StanWezlowService(Wezly wezly) {
        this.wezly = wezly;
    }

    public synchronized StanWezlow stan() {
        if (ostatniPomiar != null && czasOstatniegoPomiaru.plus(WAZNOSC_POMIARU).isAfter(Instant.now())) {
            return ostatniPomiar;
        }
        WynikZWezlow<StanWezla> wyniki = wezly.zapytajWszystkie(this::zmierz);
        List<StanWezla> stany = new ArrayList<>();
        for (Wezel wezel : wezly.wszystkie()) {
            StanWezla stan = wyniki.wyniki().get(wezel.kodSieci());
            stany.add(stan != null ? stan : niedostepny(wezel));
        }
        ostatniPomiar = new StanWezlow(CzasUtc.teraz(), stany);
        czasOstatniegoPomiaru = Instant.now();
        return ostatniPomiar;
    }

    private StanWezla zmierz(Wezel wezel) {
        long start = System.nanoTime();
        wezel.jdbc().sql("SELECT 1 FROM dual").query(Integer.class).single();
        long czasOdpowiedzi = Duration.ofNanos(System.nanoTime() - start).toMillis();
        int wiszace = wezel.jdbc().sql("SELECT COUNT(*) FROM sys.dba_2pc_pending").query(Integer.class).single();
        if (wezel.glowny()) {
            return new StanWezla(wezel.kodSieci(), wezel.nr(), "GLOWNY", true, czasOdpowiedzi, null, logiMv(wezel), wiszace, null);
        }
        return new StanWezla(wezel.kodSieci(), wezel.nr(), "REPLIKA", true, czasOdpowiedzi, replikacja(wezel), null, wiszace, null);
    }

    private static StanReplikacji replikacja(Wezel wezel) {
        LocalDateTime ostatnie = wezel.jdbc().sql("SELECT MIN(last_refresh_date) FROM user_mviews")
                .query((rs, i) -> rs.getObject(1, LocalDateTime.class)).single();
        OffsetDateTime ostatnieUtc = ostatnie == null ? null : ostatnie.atOffset(ZoneOffset.UTC);
        Long opoznienie = ostatnieUtc == null ? null : Duration.between(ostatnieUtc.toInstant(), Instant.now()).toSeconds();
        List<BladOdswiezania> bledy = wezel.jdbc().sql("""
                SELECT log_date, additional_info
                  FROM user_scheduler_job_run_details
                 WHERE job_name = 'ODSWIEZ_DANE_WSPOLNE' AND status <> 'SUCCEEDED'
                 ORDER BY log_date DESC
                 FETCH FIRST 1 ROW ONLY
                """)
                .query((rs, i) -> new BladOdswiezania(
                        rs.getObject(1, OffsetDateTime.class).withOffsetSameInstant(ZoneOffset.UTC),
                        skroc(rs.getString(2))))
                .list();
        BladOdswiezania ostatniBlad = bledy.stream()
                .filter(blad -> ostatnieUtc == null || blad.czas().isAfter(ostatnieUtc))
                .findFirst()
                .orElse(null);
        boolean przekroczono = opoznienie == null || opoznienie > LIMIT_OPOZNIENIA_REPLIK.toSeconds();
        return new StanReplikacji(ostatnieUtc, opoznienie, przekroczono, ostatniBlad);
    }

    private static List<LogMv> logiMv(Wezel wezel) {
        Map<String, Long> rozmiary = new java.util.HashMap<>();
        wezel.jdbc().sql("SELECT segment_name, bytes FROM user_segments WHERE segment_name LIKE 'MLOG$%'")
                .query(rs -> {
                    rozmiary.put(rs.getString(1), rs.getLong(2));
                });
        List<String[]> logi = wezel.jdbc().sql("SELECT master, log_table FROM user_mview_logs ORDER BY master")
                .query((rs, i) -> new String[] { rs.getString(1), rs.getString(2) })
                .list();
        List<LogMv> wynik = new ArrayList<>();
        for (String[] log : logi) {
            long wiersze = wezel.jdbc().sql("SELECT COUNT(*) FROM \"" + log[1] + "\"").query(Long.class).single();
            wynik.add(new LogMv(log[0], wiersze, rozmiary.getOrDefault(log[1], 0L)));
        }
        return wynik;
    }

    private static StanWezla niedostepny(Wezel wezel) {
        return new StanWezla(wezel.kodSieci(), wezel.nr(), wezel.glowny() ? "GLOWNY" : "REPLIKA", false, null, null, null, null,
                "Węzeł niedostępny");
    }

    private static String skroc(String tekst) {
        if (tekst == null) {
            return null;
        }
        String jednaLinia = tekst.replaceAll("\\s+", " ").trim();
        return jednaLinia.length() > 300 ? jednaLinia.substring(0, 300) : jednaLinia;
    }
}
