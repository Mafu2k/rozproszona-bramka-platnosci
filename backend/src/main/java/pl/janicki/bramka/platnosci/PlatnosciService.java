package pl.janicki.bramka.platnosci;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import pl.janicki.bramka.bledy.BladApi;
import pl.janicki.bramka.bledy.BledyBazy;
import pl.janicki.bramka.monitoring.MetrykiPlatnosci;
import pl.janicki.bramka.numer.NumerKarty;
import pl.janicki.bramka.numer.TablicaBin;
import pl.janicki.bramka.numer.TokenKarty;
import pl.janicki.bramka.numer.ZakresBin;
import pl.janicki.bramka.wezly.Wezel;
import pl.janicki.bramka.wezly.Wezly;
import pl.janicki.bramka.wezly.WynikZWezlow;
import pl.janicki.bramka.wspolne.CzasUtc;
import pl.janicki.bramka.wspolne.Kwoty;

@Service
public class PlatnosciService {

    private static final Logger log = LoggerFactory.getLogger(PlatnosciService.class);
    private static final int MAKS_PROB = 2;
    private static final Duration PRZERWA_MIEDZY_PROBAMI = Duration.ofMillis(200);
    private static final String AUTORYZACJA = "AUTORYZACJA";
    private static final String ZWROT = "ZWROT";

    private static final String STATUS_ZADANIA = """
            SELECT t.id_zadania, t.rrn, t.typ, t.kwota, t.waluta, t.status, t.kod_odpowiedzi, t.data_transakcji,
                   k.numer_maskowany, p.rrn AS rrn_pierwotnej
              FROM transakcja t
              JOIN karta k ON k.id_karty = t.id_karty
              LEFT JOIN transakcja p ON p.id_transakcji = t.id_transakcji_pierwotnej
             WHERE t.id_sklepu = ? AND t.id_zadania = ?
            """;

    private final Wezly wezly;
    private final TablicaBin tablicaBin;
    private final TokenKarty tokenKarty;
    private final ProceduryPlatnosci procedury;
    private final MetrykiPlatnosci metryki;

    public PlatnosciService(Wezly wezly, TablicaBin tablicaBin, TokenKarty tokenKarty, ProceduryPlatnosci procedury,
            MetrykiPlatnosci metryki) {
        this.wezly = wezly;
        this.tablicaBin = tablicaBin;
        this.tokenKarty = tokenKarty;
        this.procedury = procedury;
        this.metryki = metryki;
    }

    public WynikPlatnosci autoryzuj(long idSklepu, ZadanieAutoryzacji zadanie) {
        long poczatek = System.nanoTime();
        String siec = null;
        try {
            NumerKarty numer = NumerKarty.z(zadanie.numerKarty());
            ZakresBin zakres = tablicaBin.rozpoznaj(numer).orElseThrow(BladApi::nieprawidlowyNumerKarty);
            Wezel wezel = wezly.wezel(zakres.nrWezla());
            siec = wezel.kodSieci();
            String token = tokenKarty.token(numer);
            log.info("Autoryzacja sklep={} zadanie={} karta={} kwota={} {} siec={}",
                    idSklepu, zadanie.idZadania(), numer.maska(), zadanie.kwota(), zadanie.waluta(), siec);
            WynikProcedury wynik = zPonowieniem(wezel,
                    () -> procedury.autoryzuj(wezel, token, idSklepu, zadanie.idZadania(), zadanie.kwota(), zadanie.waluta()));
            log.info("Decyzja zadanie={} kod={} rrn={} powtorzona={}", zadanie.idZadania(), wynik.kod(), wynik.rrn(), wynik.powtorzona());
            WynikPlatnosci platnosc = wynikPlatnosci(zadanie.idZadania(), AUTORYZACJA, zadanie.kwota(), zadanie.waluta(), siec,
                    Optional.ofNullable(wynik.maska()).orElse(numer.maska()), wynik, null);
            metryki.zarejestruj(platnosc, poczatek);
            return platnosc;
        } catch (RuntimeException e) {
            metryki.zarejestrujBlad(siec, AUTORYZACJA, e, poczatek);
            throw e;
        }
    }

    public WynikPlatnosci zwroc(long idSklepu, ZadanieZwrotu zadanie) {
        long poczatek = System.nanoTime();
        String siec = null;
        try {
            Optional<Wezel> wezelPierwotnej = wezly.wezelDlaRrn(zadanie.rrnPierwotnej());
            if (wezelPierwotnej.isEmpty()) {
                WynikPlatnosci brakPierwotnej = new WynikPlatnosci(zadanie.idZadania(), WynikPlatnosci.Decyzja.ODRZUCONA, "25",
                        KodOdpowiedzi.komunikat("25"), null, ZWROT, zadanie.kwota(), zadanie.waluta(), null, null, null, null,
                        zadanie.rrnPierwotnej(), false);
                metryki.zarejestruj(brakPierwotnej, poczatek);
                return brakPierwotnej;
            }
            Wezel wezel = wezelPierwotnej.get();
            siec = wezel.kodSieci();
            log.info("Zwrot sklep={} zadanie={} rrnPierwotnej={} kwota={} {} siec={}",
                    idSklepu, zadanie.idZadania(), zadanie.rrnPierwotnej(), zadanie.kwota(), zadanie.waluta(), siec);
            WynikProcedury wynik = zPonowieniem(wezel,
                    () -> procedury.zwroc(wezel, zadanie.rrnPierwotnej(), idSklepu, zadanie.idZadania(), zadanie.kwota(), zadanie.waluta()));
            WynikPlatnosci platnosc = wynikPlatnosci(zadanie.idZadania(), ZWROT, zadanie.kwota(), zadanie.waluta(), siec,
                    wynik.maska(), wynik, zadanie.rrnPierwotnej());
            metryki.zarejestruj(platnosc, poczatek);
            return platnosc;
        } catch (RuntimeException e) {
            metryki.zarejestrujBlad(siec, ZWROT, e, poczatek);
            throw e;
        }
    }

    public WynikPlatnosci status(long idSklepu, String idZadania) {
        WynikZWezlow<Optional<WynikPlatnosci>> wyniki = wezly.zapytajWszystkie(wezel -> wezel.jdbc()
                .sql(STATUS_ZADANIA)
                .params(idSklepu, idZadania)
                .query((rs, i) -> {
                    String kod = rs.getString("kod_odpowiedzi");
                    return new WynikPlatnosci(rs.getString("id_zadania"), WynikPlatnosci.decyzja(kod), kod,
                            KodOdpowiedzi.komunikat(kod), rs.getString("rrn"), rs.getString("typ"),
                            Kwoty.naGrosze(rs.getBigDecimal("kwota")), rs.getString("waluta"), wezel.kodSieci(),
                            rs.getString("numer_maskowany"), rs.getString("status"), CzasUtc.odczytaj(rs, "data_transakcji"),
                            rs.getString("rrn_pierwotnej"), true);
                })
                .optional());
        Optional<WynikPlatnosci> znaleziony = wyniki.wyniki().values().stream().flatMap(Optional::stream).findFirst();
        if (znaleziony.isPresent()) {
            return znaleziony.get();
        }
        if (wyniki.czesciowy()) {
            throw new BladApi(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "91",
                    "Nie można ustalić wyniku - część sieci jest niedostępna", Map.of("brakujaceSieci", List.copyOf(wyniki.brakujaceSieci())), null);
        }
        throw BladApi.nieZnaleziono("Nie znaleziono żądania o tym identyfikatorze");
    }

    private WynikPlatnosci wynikPlatnosci(String idZadania, String typ, long kwota, String waluta, String siec, String maska,
            WynikProcedury wynik, String rrnPierwotnej) {
        boolean zapisana = wynik.rrn() != null;
        return new WynikPlatnosci(idZadania, WynikPlatnosci.decyzja(wynik.kod()), wynik.kod(), KodOdpowiedzi.komunikat(wynik.kod()),
                wynik.rrn(), typ, kwota, waluta, siec, maska, zapisana ? wynik.status() : null, wynik.data(), rrnPierwotnej,
                wynik.powtorzona());
    }

    private WynikProcedury zPonowieniem(Wezel wezel, Supplier<WynikProcedury> wywolanie) {
        for (int proba = 1;; proba++) {
            try {
                return wywolanie.get();
            } catch (RuntimeException e) {
                if (proba < MAKS_PROB && BledyBazy.wezelNiedostepny(e)) {
                    log.warn("Węzeł {} nie odpowiedział (próba {}), ponawiam z tym samym idZadania", wezel.kodSieci(), proba);
                    czekaj(PRZERWA_MIEDZY_PROBAMI.multipliedBy(proba));
                    continue;
                }
                throw wezel.przetlumacz(e);
            }
        }
    }

    private static void czekaj(Duration czas) {
        try {
            Thread.sleep(czas);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
