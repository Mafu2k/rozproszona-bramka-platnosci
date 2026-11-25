package pl.janicki.bramka.karty;

import java.sql.CallableStatement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Service;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import pl.janicki.bramka.bledy.BladApi;
import pl.janicki.bramka.idempotencja.IdempotencjaPanelu;
import pl.janicki.bramka.numer.GeneratorNumeruKarty;
import pl.janicki.bramka.numer.NumerKarty;
import pl.janicki.bramka.numer.TokenKarty;
import pl.janicki.bramka.platnosci.WzorceDanych;
import pl.janicki.bramka.uwierzytelnianie.ZalogowanyUzytkownik;
import pl.janicki.bramka.wezly.Wezel;
import pl.janicki.bramka.wezly.Wezly;
import pl.janicki.bramka.wezly.WynikZWezlow;
import pl.janicki.bramka.wspolne.Kwoty;
import pl.janicki.bramka.wspolne.Strona;
import pl.janicki.bramka.wspolne.Stronicowanie;
import pl.janicki.bramka.wspolne.ZapytanieSql;

@Service
public class KartyService {

    private static final int PROBY_WYGENEROWANIA_NUMERU = 3;

    public record KartaNowa(
            @NotNull @Min(1) Long idKlienta,
            @NotNull @Min(0) @Max(WzorceDanych.MAKS_KWOTA) Long limitKredytowy,
            @Pattern(regexp = WzorceDanych.WALUTA) String waluta,
            @Min(1) @Max(60) Integer miesiaceWaznosci) {
    }

    public record ZmianaLimitu(@NotNull @Min(0) @Max(WzorceDanych.MAKS_KWOTA) Long limitKredytowy) {
    }

    public record KartaWydana(String numerKarty, Karta karta) {
    }

    private record Fragment(List<Karta> karty, long lacznie) {
    }

    private final Wezly wezly;
    private final IdempotencjaPanelu idempotencja;
    private final GeneratorNumeruKarty generator;
    private final TokenKarty tokenKarty;

    public KartyService(Wezly wezly, IdempotencjaPanelu idempotencja, GeneratorNumeruKarty generator, TokenKarty tokenKarty) {
        this.wezly = wezly;
        this.idempotencja = idempotencja;
        this.generator = generator;
        this.tokenKarty = tokenKarty;
    }

    public Strona<Karta> lista(String siec, Long idKlienta, String status, Stronicowanie stronicowanie, ZalogowanyUzytkownik uzytkownik) {
        Collection<Wezel> wezlyDoOdpytania = wezlyWidoczneDla(uzytkownik, siec);
        ZapytanieSql warunki = new ZapytanieSql()
                .warunek(idKlienta != null, "id_klienta = ?", idKlienta)
                .warunek(status != null, "status = ?", status);
        WynikZWezlow<Fragment> wyniki = wezly.zapytaj(wezlyDoOdpytania, wezel -> {
            long lacznie = wezel.jdbc().sql("SELECT COUNT(*) FROM karta" + warunki.where())
                    .params(warunki.parametry()).query(Long.class).single();
            List<Karta> karty = wezel.jdbc().sql("SELECT " + Karta.KOLUMNY + " FROM karta" + warunki.where()
                    + " ORDER BY data_wydania DESC, id_karty DESC FETCH FIRST " + stronicowanie.doPobraniaZKazdegoWezla() + " ROWS ONLY")
                    .params(warunki.parametry())
                    .query((rs, i) -> Karta.zWiersza(rs, wezel.kodSieci()))
                    .list();
            return new Fragment(karty, lacznie);
        });
        List<Karta> wszystkie = new ArrayList<>();
        long lacznie = 0;
        for (Fragment fragment : wyniki.wyniki().values()) {
            wszystkie.addAll(fragment.karty());
            lacznie += fragment.lacznie();
        }
        List<Karta> strona = wszystkie.stream()
                .sorted(Comparator.comparing(Karta::dataWydania).reversed().thenComparing(Comparator.comparingLong(Karta::id).reversed()))
                .skip(stronicowanie.przesuniecie())
                .limit(stronicowanie.rozmiar())
                .toList();
        return new Strona<>(strona, stronicowanie.strona(), stronicowanie.rozmiar(), lacznie, null, wyniki.czesciowy(),
                wyniki.brakujaceSieci());
    }

    public Karta pobierz(long idKarty, ZalogowanyUzytkownik uzytkownik) {
        Wezel wezel = wezelKarty(idKarty, uzytkownik);
        try {
            return odczytaj(wezel, idKarty).orElseThrow(() -> BladApi.nieZnaleziono("Karta nie istnieje"));
        } catch (RuntimeException e) {
            throw wezel.przetlumacz(e);
        }
    }

    public IdempotencjaPanelu.Odpowiedz<KartaWydana> wydaj(KartaNowa dane, String klucz, ZalogowanyUzytkownik uzytkownik) {
        Wezel wezel = wezly.wezelSieci(uzytkownik.siec())
                .orElseThrow(() -> BladApi.brakUprawnien("Operator nie ma przypisanej sieci"));
        String waluta = dane.waluta() == null ? "PLN" : dane.waluta();
        int miesiace = dane.miesiaceWaznosci() == null ? 36 : dane.miesiaceWaznosci();
        LocalDate dataWaznosci = YearMonth.now(ZoneOffset.UTC).plusMonths(miesiace).atEndOfMonth();
        return idempotencja.wykonaj(wezel, uzytkownik, klucz, "POST /karty", dane, KartaWydana.class, () -> {
            for (int proba = 1;; proba++) {
                NumerKarty numer = generator.nowyNumer(wezel.kodSieci());
                try {
                    long idKarty = wydajWBazie(wezel, dane.idKlienta(), tokenKarty.token(numer), numer.maska(), dataWaznosci,
                            dane.limitKredytowy(), waluta);
                    return new KartaWydana(numer.cyfry(), odczytaj(wezel, idKarty).orElseThrow());
                } catch (DuplicateKeyException e) {
                    if (proba == PROBY_WYGENEROWANIA_NUMERU) {
                        throw e;
                    }
                }
            }
        }, wydana -> new KartaWydana(null, wydana.karta()));
    }

    public IdempotencjaPanelu.Odpowiedz<Karta> zmienLimit(long idKarty, ZmianaLimitu zmiana, String klucz, ZalogowanyUzytkownik uzytkownik) {
        Wezel wezel = wezelKarty(idKarty, uzytkownik);
        return idempotencja.wykonaj(wezel, uzytkownik, klucz, "PUT /karty/" + idKarty + "/limit", zmiana, Karta.class, () -> {
            wezel.jdbc().sql("CALL pkg_karty.zmien_limit(?, ?)")
                    .params(idKarty, Kwoty.zGroszy(zmiana.limitKredytowy())).update();
            return odczytaj(wezel, idKarty).orElseThrow();
        });
    }

    public IdempotencjaPanelu.Odpowiedz<Karta> zablokuj(long idKarty, String klucz, ZalogowanyUzytkownik uzytkownik) {
        return zmienStatus(idKarty, klucz, uzytkownik, "zablokuj");
    }

    public IdempotencjaPanelu.Odpowiedz<Karta> odblokuj(long idKarty, String klucz, ZalogowanyUzytkownik uzytkownik) {
        return zmienStatus(idKarty, klucz, uzytkownik, "odblokuj");
    }

    public List<Karta> kartyKlienta(Wezel wezel, long idKlienta) {
        return wezel.jdbc().sql("SELECT " + Karta.KOLUMNY + " FROM karta WHERE id_klienta = ? ORDER BY id_karty")
                .param(idKlienta)
                .query((rs, i) -> Karta.zWiersza(rs, wezel.kodSieci()))
                .list();
    }

    public Collection<Wezel> wezlyWidoczneDla(ZalogowanyUzytkownik uzytkownik, String siec) {
        if (siec != null) {
            uzytkownik.sprawdzDostepDoSieci(siec);
            return List.of(wezly.wezelSieci(siec).orElseThrow(() -> BladApi.walidacja("Nieznana sieć " + siec, null)));
        }
        if (uzytkownik.operator()) {
            return List.of(wezly.wezelSieci(uzytkownik.siec()).orElseThrow());
        }
        return wezly.wszystkie();
    }

    private IdempotencjaPanelu.Odpowiedz<Karta> zmienStatus(long idKarty, String klucz, ZalogowanyUzytkownik uzytkownik, String procedura) {
        Wezel wezel = wezelKarty(idKarty, uzytkownik);
        return idempotencja.wykonaj(wezel, uzytkownik, klucz, "POST /karty/" + idKarty + "/" + procedura, null, Karta.class, () -> {
            wezel.jdbc().sql("CALL pkg_karty." + procedura + "(?)").param(idKarty).update();
            return odczytaj(wezel, idKarty).orElseThrow();
        });
    }

    private Wezel wezelKarty(long idKarty, ZalogowanyUzytkownik uzytkownik) {
        Wezel wezel = wezly.wezelDlaIdentyfikatora(idKarty).orElseThrow(() -> BladApi.nieZnaleziono("Karta nie istnieje"));
        uzytkownik.sprawdzDostepDoSieci(wezel.kodSieci());
        return wezel;
    }

    private static Optional<Karta> odczytaj(Wezel wezel, long idKarty) {
        return wezel.jdbc().sql("SELECT " + Karta.KOLUMNY + " FROM karta WHERE id_karty = ?")
                .param(idKarty)
                .query((rs, i) -> Karta.zWiersza(rs, wezel.kodSieci()))
                .optional();
    }

    private static long wydajWBazie(Wezel wezel, long idKlienta, String token, String maska, LocalDate dataWaznosci,
            long limitWGroszach, String waluta) {
        return wezel.jdbcTemplate().execute((ConnectionCallback<Long>) polaczenie -> {
            try (CallableStatement procedura = polaczenie.prepareCall("{call pkg_karty.wydaj(?, ?, ?, ?, ?, ?, ?)}")) {
                procedura.setLong(1, idKlienta);
                procedura.setString(2, token);
                procedura.setString(3, maska);
                procedura.setObject(4, dataWaznosci);
                procedura.setBigDecimal(5, Kwoty.zGroszy(limitWGroszach));
                procedura.setString(6, waluta);
                procedura.registerOutParameter(7, Types.NUMERIC);
                procedura.execute();
                return procedura.getLong(7);
            }
        });
    }
}
