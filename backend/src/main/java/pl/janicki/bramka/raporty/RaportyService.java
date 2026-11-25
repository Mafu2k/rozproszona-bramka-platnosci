package pl.janicki.bramka.raporty;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonProperty;

import pl.janicki.bramka.bledy.BladApi;
import pl.janicki.bramka.sklepy.SklepyService;
import pl.janicki.bramka.wezly.Wezly;
import pl.janicki.bramka.wezly.WynikZWezlow;
import pl.janicki.bramka.wspolne.CzasUtc;
import pl.janicki.bramka.wspolne.Kwoty;

@Service
public class RaportyService {

    public record ObrotySieci(String siec, long liczbaAutoryzacji, long sumaAutoryzacji, long liczbaZwrotow, long sumaZwrotow) {
    }

    public record RaportObrotow(long idSklepu, String nazwaSklepu, OffsetDateTime od, @JsonProperty("do") OffsetDateTime doCzasu,
            List<ObrotySieci> sieci, ObrotySieci lacznie, boolean czesciowy, List<String> brakujaceSieci) {
    }

    public record StatystykaSieci(String siec, long liczbaTransakcji, long sumaKwot, long liczbaOdrzuconych, double odsetekOdrzuconych) {
    }

    public record StatystykiSieci(OffsetDateTime od, @JsonProperty("do") OffsetDateTime doCzasu, List<StatystykaSieci> sieci, boolean czesciowy,
            List<String> brakujaceSieci) {
    }

    private static final String OBROTY = """
            SELECT
              COUNT(CASE WHEN typ = 'AUTORYZACJA' AND status IN ('AUTORYZOWANA', 'ZWROCONA') THEN 1 END),
              NVL(SUM(CASE WHEN typ = 'AUTORYZACJA' AND status IN ('AUTORYZOWANA', 'ZWROCONA') THEN kwota END), 0),
              COUNT(CASE WHEN typ = 'ZWROT' AND status = 'AUTORYZOWANA' THEN 1 END),
              NVL(SUM(CASE WHEN typ = 'ZWROT' AND status = 'AUTORYZOWANA' THEN kwota END), 0)
              FROM transakcja
             WHERE id_sklepu = ? AND data_transakcji >= ? AND data_transakcji < ?
            """;

    private static final String STATYSTYKI = """
            SELECT
              COUNT(*),
              NVL(SUM(CASE WHEN kod_odpowiedzi = '00' THEN kwota END), 0),
              COUNT(CASE WHEN status = 'ODRZUCONA' THEN 1 END)
              FROM transakcja
             WHERE typ = 'AUTORYZACJA' AND data_transakcji >= ? AND data_transakcji < ?
            """;

    private final Wezly wezly;
    private final SklepyService sklepy;

    public RaportyService(Wezly wezly, SklepyService sklepy) {
        this.wezly = wezly;
        this.sklepy = sklepy;
    }

    public RaportObrotow obrotySklepu(long idSklepu, OffsetDateTime od, OffsetDateTime doCzasu) {
        sprawdzOkres(od, doCzasu);
        String nazwa = sklepy.pobierz(idSklepu).nazwa();
        WynikZWezlow<ObrotySieci> wyniki = wezly.zapytajWszystkie(wezel -> wezel.jdbc().sql(OBROTY)
                .params(idSklepu, CzasUtc.doBazy(od), CzasUtc.doBazy(doCzasu))
                .query((rs, i) -> new ObrotySieci(wezel.kodSieci(), rs.getLong(1), Kwoty.naGrosze(rs.getBigDecimal(2)),
                        rs.getLong(3), Kwoty.naGrosze(rs.getBigDecimal(4))))
                .single());
        List<ObrotySieci> sieci = new ArrayList<>(wyniki.wyniki().values());
        ObrotySieci lacznie = new ObrotySieci(null,
                sieci.stream().mapToLong(ObrotySieci::liczbaAutoryzacji).sum(),
                sieci.stream().mapToLong(ObrotySieci::sumaAutoryzacji).sum(),
                sieci.stream().mapToLong(ObrotySieci::liczbaZwrotow).sum(),
                sieci.stream().mapToLong(ObrotySieci::sumaZwrotow).sum());
        return new RaportObrotow(idSklepu, nazwa, od, doCzasu, sieci, lacznie, wyniki.czesciowy(), wyniki.brakujaceSieci());
    }

    public StatystykiSieci statystykiSieci(OffsetDateTime od, OffsetDateTime doCzasu) {
        sprawdzOkres(od, doCzasu);
        WynikZWezlow<StatystykaSieci> wyniki = wezly.zapytajWszystkie(wezel -> wezel.jdbc().sql(STATYSTYKI)
                .params(CzasUtc.doBazy(od), CzasUtc.doBazy(doCzasu))
                .query((rs, i) -> {
                    long liczba = rs.getLong(1);
                    long odrzucone = rs.getLong(3);
                    double odsetek = liczba == 0 ? 0.0
                            : BigDecimal.valueOf(odrzucone * 100.0 / liczba).setScale(2, RoundingMode.HALF_UP).doubleValue();
                    return new StatystykaSieci(wezel.kodSieci(), liczba, Kwoty.naGrosze(rs.getBigDecimal(2)), odrzucone, odsetek);
                })
                .single());
        return new StatystykiSieci(od, doCzasu, new ArrayList<>(wyniki.wyniki().values()), wyniki.czesciowy(), wyniki.brakujaceSieci());
    }

    private static void sprawdzOkres(OffsetDateTime od, OffsetDateTime doCzasu) {
        if (!od.isBefore(doCzasu)) {
            throw BladApi.walidacja("Nieprawidłowy okres raportu", Map.of("od", "musi być wcześniej niż do"));
        }
    }
}
