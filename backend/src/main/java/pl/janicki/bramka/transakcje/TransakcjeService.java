package pl.janicki.bramka.transakcje;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonInclude;

import pl.janicki.bramka.bledy.BladApi;
import pl.janicki.bramka.karty.KartyService;
import pl.janicki.bramka.uwierzytelnianie.ZalogowanyUzytkownik;
import pl.janicki.bramka.wezly.Wezel;
import pl.janicki.bramka.wezly.Wezly;
import pl.janicki.bramka.wezly.WynikZWezlow;
import pl.janicki.bramka.wspolne.CzasUtc;
import pl.janicki.bramka.wspolne.Kwoty;
import pl.janicki.bramka.wspolne.Strona;
import pl.janicki.bramka.wspolne.Stronicowanie;
import pl.janicki.bramka.wspolne.ZapytanieSql;

@Service
public class TransakcjeService {

    public record Filtr(
            String siec,
            Long idKarty,
            Long idSklepu,
            String rrn,
            String status,
            String typ,
            OffsetDateTime od,
            OffsetDateTime doCzasu,
            Long kwotaOd,
            Long kwotaDo,
            String sortuj,
            String kierunek) {
    }

    public record TransakcjaSzczegoly(Transakcja transakcja, @JsonInclude(JsonInclude.Include.NON_NULL) Transakcja pierwotna,
            List<Transakcja> zwroty) {
    }

    private record Fragment(List<Transakcja> transakcje, long lacznie) {
    }

    private final Wezly wezly;
    private final KartyService karty;

    public TransakcjeService(Wezly wezly, KartyService karty) {
        this.wezly = wezly;
        this.karty = karty;
    }

    public Strona<Transakcja> lista(Filtr filtr, Stronicowanie stronicowanie, ZalogowanyUzytkownik uzytkownik) {
        Collection<Wezel> doOdpytania = wezlyDlaFiltra(filtr, uzytkownik);
        ZapytanieSql warunki = new ZapytanieSql()
                .warunek(filtr.idKarty() != null, "t.id_karty = ?", filtr.idKarty())
                .warunek(filtr.idSklepu() != null, "t.id_sklepu = ?", filtr.idSklepu())
                .warunek(filtr.rrn() != null, "t.rrn = ?", filtr.rrn())
                .warunek(filtr.status() != null, "t.status = ?", filtr.status())
                .warunek(filtr.typ() != null, "t.typ = ?", filtr.typ())
                .warunek(filtr.od() != null, "t.data_transakcji >= ?", CzasUtc.doBazy(filtr.od()))
                .warunek(filtr.doCzasu() != null, "t.data_transakcji < ?", CzasUtc.doBazy(filtr.doCzasu()))
                .warunek(filtr.kwotaOd() != null, "t.kwota >= ?", filtr.kwotaOd() == null ? null : Kwoty.zGroszy(filtr.kwotaOd()))
                .warunek(filtr.kwotaDo() != null, "t.kwota <= ?", filtr.kwotaDo() == null ? null : Kwoty.zGroszy(filtr.kwotaDo()));
        boolean wedlugKwoty = "kwota".equals(filtr.sortuj());
        boolean rosnaco = "asc".equals(filtr.kierunek());
        String kolejnosc = (wedlugKwoty ? "t.kwota" : "t.data_transakcji") + (rosnaco ? " ASC" : " DESC")
                + ", t.id_transakcji" + (rosnaco ? " ASC" : " DESC");
        WynikZWezlow<Fragment> wyniki = wezly.zapytaj(doOdpytania, wezel -> new Fragment(
                wezel.jdbc().sql("SELECT " + Transakcja.KOLUMNY + Transakcja.ZRODLO + warunki.where()
                        + " ORDER BY " + kolejnosc + " FETCH FIRST " + stronicowanie.doPobraniaZKazdegoWezla() + " ROWS ONLY")
                        .params(warunki.parametry())
                        .query((rs, i) -> Transakcja.zWiersza(rs, wezel.kodSieci()))
                        .list(),
                wezel.jdbc().sql("SELECT COUNT(*)" + Transakcja.ZRODLO + warunki.where())
                        .params(warunki.parametry()).query(Long.class).single()));
        Comparator<Transakcja> porownanie = wedlugKwoty
                ? Comparator.comparingLong(Transakcja::kwota)
                : Comparator.comparing(Transakcja::dataTransakcji);
        porownanie = porownanie.thenComparingLong(Transakcja::id);
        if (!rosnaco) {
            porownanie = porownanie.reversed();
        }
        List<Transakcja> wszystkie = new ArrayList<>();
        long lacznie = 0;
        for (Fragment fragment : wyniki.wyniki().values()) {
            wszystkie.addAll(fragment.transakcje());
            lacznie += fragment.lacznie();
        }
        List<Transakcja> strona = wszystkie.stream()
                .sorted(porownanie)
                .skip(stronicowanie.przesuniecie())
                .limit(stronicowanie.rozmiar())
                .toList();
        return new Strona<>(strona, stronicowanie.strona(), stronicowanie.rozmiar(), lacznie, null, wyniki.czesciowy(),
                wyniki.brakujaceSieci());
    }

    public TransakcjaSzczegoly szczegoly(long idTransakcji, ZalogowanyUzytkownik uzytkownik) {
        Wezel wezel = wezly.wezelDlaIdentyfikatora(idTransakcji)
                .orElseThrow(() -> BladApi.nieZnaleziono("Transakcja nie istnieje"));
        uzytkownik.sprawdzDostepDoSieci(wezel.kodSieci());
        try {
            Transakcja transakcja = odczytaj(wezel, idTransakcji)
                    .orElseThrow(() -> BladApi.nieZnaleziono("Transakcja nie istnieje"));
            Transakcja pierwotna = transakcja.idTransakcjiPierwotnej() == null ? null
                    : odczytaj(wezel, transakcja.idTransakcjiPierwotnej()).orElse(null);
            List<Transakcja> zwroty = wezel.jdbc()
                    .sql("SELECT " + Transakcja.KOLUMNY + Transakcja.ZRODLO + " WHERE t.id_transakcji_pierwotnej = ? ORDER BY t.id_transakcji")
                    .param(idTransakcji)
                    .query((rs, i) -> Transakcja.zWiersza(rs, wezel.kodSieci()))
                    .list();
            return new TransakcjaSzczegoly(transakcja, pierwotna, zwroty);
        } catch (RuntimeException e) {
            throw wezel.przetlumacz(e);
        }
    }

    private Collection<Wezel> wezlyDlaFiltra(Filtr filtr, ZalogowanyUzytkownik uzytkownik) {
        Optional<Wezel> wskazany = Optional.empty();
        if (filtr.rrn() != null) {
            wskazany = Optional.of(wezly.wezelDlaRrn(filtr.rrn()).orElseThrow(() -> BladApi.walidacja("Nieprawidłowy RRN", null)));
        } else if (filtr.idKarty() != null) {
            wskazany = Optional.of(wezly.wezelDlaIdentyfikatora(filtr.idKarty())
                    .orElseThrow(() -> BladApi.walidacja("Nieprawidłowy identyfikator karty", null)));
        }
        if (wskazany.isPresent()) {
            Wezel wezel = wskazany.get();
            if (filtr.siec() != null && !filtr.siec().equals(wezel.kodSieci())) {
                return List.of();
            }
            uzytkownik.sprawdzDostepDoSieci(wezel.kodSieci());
            return List.of(wezel);
        }
        return karty.wezlyWidoczneDla(uzytkownik, filtr.siec());
    }

    private static Optional<Transakcja> odczytaj(Wezel wezel, long idTransakcji) {
        return wezel.jdbc().sql("SELECT " + Transakcja.KOLUMNY + Transakcja.ZRODLO + " WHERE t.id_transakcji = ?")
                .param(idTransakcji)
                .query((rs, i) -> Transakcja.zWiersza(rs, wezel.kodSieci()))
                .optional();
    }
}
