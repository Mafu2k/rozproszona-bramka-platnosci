package pl.janicki.bramka.numer;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import pl.janicki.bramka.bledy.BladApi;
import pl.janicki.bramka.wezly.Wezly;

@Component
public class TablicaBin {

    private static final Logger log = LoggerFactory.getLogger(TablicaBin.class);

    private static final String ZAPYTANIE = """
            SELECT z.bin_od, z.bin_do, z.dlugosc_pan, s.kod, s.nr_wezla
              FROM zakres_bin z
              JOIN siec_kartowa s ON s.id_sieci = z.id_sieci
            """;

    private final Wezly wezly;
    private volatile NavigableMap<Long, ZakresBin> zakresy = Collections.emptyNavigableMap();
    private volatile Instant ostatnieOdswiezenie;

    public TablicaBin(Wezly wezly) {
        this.wezly = wezly;
    }

    @Scheduled(fixedDelayString = "PT30S")
    public void odswiez() {
        try {
            List<ZakresBin> wczytane = wezly.czytajDaneWspolne(wezel -> wezel.jdbc().sql(ZAPYTANIE)
                    .query((rs, i) -> new ZakresBin(rs.getLong(1), rs.getLong(2), rs.getInt(3), rs.getString(4), rs.getInt(5)))
                    .list()).wartosc();
            NavigableMap<Long, ZakresBin> nowe = new TreeMap<>();
            wczytane.forEach(z -> nowe.put(z.binOd(), z));
            zakresy = Collections.unmodifiableNavigableMap(nowe);
            ostatnieOdswiezenie = Instant.now();
        } catch (RuntimeException e) {
            log.warn("Nie udało się odświeżyć tablicy BIN, zostaje poprzednia wersja: {}", e.getMessage());
        }
    }

    public Optional<ZakresBin> rozpoznaj(NumerKarty numer) {
        if (zakresy.isEmpty()) {
            odswiez();
            if (zakresy.isEmpty()) {
                throw BladApi.siecNiedostepna("VISA");
            }
        }
        Map.Entry<Long, ZakresBin> kandydat = zakresy.floorEntry(numer.bin8());
        return Optional.ofNullable(kandydat).map(Map.Entry::getValue).filter(z -> z.obejmuje(numer));
    }

    public List<ZakresBin> zakresySieci(String kodSieci) {
        if (zakresy.isEmpty()) {
            odswiez();
        }
        return zakresy.values().stream().filter(z -> z.kodSieci().equals(kodSieci)).toList();
    }

    public Instant ostatnieOdswiezenie() {
        return ostatnieOdswiezenie;
    }

    void ustaw(List<ZakresBin> nowe) {
        NavigableMap<Long, ZakresBin> mapa = new TreeMap<>();
        nowe.forEach(z -> mapa.put(z.binOd(), z));
        zakresy = Collections.unmodifiableNavigableMap(mapa);
    }
}
