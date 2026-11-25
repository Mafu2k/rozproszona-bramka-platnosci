package pl.janicki.bramka.klienci;

import java.sql.CallableStatement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pl.janicki.bramka.bledy.BladApi;
import pl.janicki.bramka.idempotencja.IdempotencjaPanelu;
import pl.janicki.bramka.karty.Karta;
import pl.janicki.bramka.karty.KartyService;
import pl.janicki.bramka.uwierzytelnianie.ZalogowanyUzytkownik;
import pl.janicki.bramka.wezly.OdczytWspolny;
import pl.janicki.bramka.wezly.Wezel;
import pl.janicki.bramka.wezly.Wezly;
import pl.janicki.bramka.wezly.WynikZWezlow;
import pl.janicki.bramka.wspolne.Strona;
import pl.janicki.bramka.wspolne.Stronicowanie;
import pl.janicki.bramka.wspolne.ZapytanieSql;

@Service
public class KlienciService {

    public record KlientDane(
            @NotBlank @Size(max = 50) String imie,
            @NotBlank @Size(max = 80) String nazwisko,
            @NotBlank @Email @Size(max = 120) String email,
            @Pattern(regexp = "^\\+?[0-9 ]{7,20}$") String telefon,
            @NotBlank @Pattern(regexp = "^[A-Z]{2}$") String kraj,
            @Pattern(regexp = "AKTYWNY|ZABLOKOWANY") String status) {
    }

    public record ListaKart(List<Karta> elementy, boolean czesciowy, List<String> brakujaceSieci) {
    }

    public record WynikBlokadyGlobalnej(long idKlienta, Map<String, Integer> zablokowaneKarty) {
    }

    private record Stron(List<Klient> elementy, long lacznie) {
    }

    private final Wezly wezly;
    private final IdempotencjaPanelu idempotencja;
    private final KartyService karty;

    public KlienciService(Wezly wezly, IdempotencjaPanelu idempotencja, KartyService karty) {
        this.wezly = wezly;
        this.idempotencja = idempotencja;
        this.karty = karty;
    }

    public Strona<Klient> lista(String szukaj, String status, Stronicowanie stronicowanie) {
        ZapytanieSql warunki = new ZapytanieSql()
                .warunek(szukaj != null && !szukaj.isBlank(),
                        "(UPPER(nazwisko) LIKE ? ESCAPE '\\' OR UPPER(imie) LIKE ? ESCAPE '\\' OR UPPER(email) LIKE ? ESCAPE '\\')",
                        wzorzec(szukaj), wzorzec(szukaj), wzorzec(szukaj))
                .warunek(status != null, "status = ?", status);
        OdczytWspolny<Stron> odczyt = wezly.czytajDaneWspolne(wezel -> new Stron(
                wezel.jdbc().sql("SELECT " + Klient.KOLUMNY + " FROM klient" + warunki.where()
                        + " ORDER BY nazwisko, imie, id_klienta OFFSET " + stronicowanie.przesuniecie()
                        + " ROWS FETCH NEXT " + stronicowanie.rozmiar() + " ROWS ONLY")
                        .params(warunki.parametry()).query((rs, i) -> Klient.zWiersza(rs)).list(),
                wezel.jdbc().sql("SELECT COUNT(*) FROM klient" + warunki.where())
                        .params(warunki.parametry()).query(Long.class).single()));
        return Strona.zJednegoZrodla(odczyt.wartosc().elementy(), stronicowanie.strona(), stronicowanie.rozmiar(),
                odczyt.wartosc().lacznie(), odczyt.zrodlo());
    }

    public Klient pobierz(long idKlienta) {
        return wezly.czytajDaneWspolne(wezel -> odczytaj(wezel, idKlienta)).wartosc();
    }

    public IdempotencjaPanelu.Odpowiedz<Klient> dodaj(KlientDane dane, String klucz, ZalogowanyUzytkownik uzytkownik) {
        Wezel glowny = wezly.glowny();
        return idempotencja.wykonaj(glowny, uzytkownik, klucz, "POST /klienci", dane, Klient.class, () -> {
            KeyHolder klucze = new GeneratedKeyHolder();
            glowny.jdbc().sql("INSERT INTO klient (imie, nazwisko, email, telefon, kraj) VALUES (?, ?, ?, ?, ?)")
                    .params(dane.imie().trim(), dane.nazwisko().trim(), dane.email().trim().toLowerCase(), dane.telefon(), dane.kraj())
                    .update(klucze, "id_klienta");
            return odczytaj(glowny, klucze.getKeyAs(java.math.BigDecimal.class).longValue());
        });
    }

    public IdempotencjaPanelu.Odpowiedz<Klient> zmien(long idKlienta, KlientDane dane, String klucz, ZalogowanyUzytkownik uzytkownik) {
        if (dane.status() == null) {
            throw BladApi.walidacja("Nieprawidłowe dane żądania", Map.of("status", "wymagany: AKTYWNY albo ZABLOKOWANY"));
        }
        Wezel glowny = wezly.glowny();
        return idempotencja.wykonaj(glowny, uzytkownik, klucz, "PUT /klienci/" + idKlienta, dane, Klient.class, () -> {
            int zmienione = glowny.jdbc().sql("""
                    UPDATE klient SET imie = ?, nazwisko = ?, email = ?, telefon = ?, kraj = ?, status = ?
                     WHERE id_klienta = ? AND status <> 'USUNIETY'
                    """)
                    .params(dane.imie().trim(), dane.nazwisko().trim(), dane.email().trim().toLowerCase(), dane.telefon(),
                            dane.kraj(), dane.status(), idKlienta)
                    .update();
            if (zmienione == 0) {
                throw BladApi.nieZnaleziono("Klient nie istnieje");
            }
            return odczytaj(glowny, idKlienta);
        });
    }

    public void usun(long idKlienta, String klucz, ZalogowanyUzytkownik uzytkownik) {
        Wezel glowny = wezly.glowny();
        idempotencja.wykonaj(glowny, uzytkownik, klucz, "DELETE /klienci/" + idKlienta, null, Void.class, () -> {
            glowny.jdbc().sql("CALL pkg_klienci.usun(?)").param(idKlienta).update();
            return null;
        });
    }

    public ListaKart karty(long idKlienta, ZalogowanyUzytkownik uzytkownik) {
        pobierz(idKlienta);
        WynikZWezlow<List<Karta>> wyniki = wezly.zapytaj(karty.wezlyWidoczneDla(uzytkownik, null),
                wezel -> karty.kartyKlienta(wezel, idKlienta));
        List<Karta> wszystkie = new ArrayList<>();
        wyniki.wyniki().values().forEach(wszystkie::addAll);
        return new ListaKart(wszystkie, wyniki.czesciowy(), wyniki.brakujaceSieci());
    }

    public IdempotencjaPanelu.Odpowiedz<WynikBlokadyGlobalnej> zablokujGlobalnie(long idKlienta, String klucz,
            ZalogowanyUzytkownik uzytkownik) {
        Wezel glowny = wezly.glowny();
        return idempotencja.wykonaj(glowny, uzytkownik, klucz, "POST /klienci/" + idKlienta + "/blokada-globalna", null,
                WynikBlokadyGlobalnej.class, () -> glowny.jdbcTemplate().execute((ConnectionCallback<WynikBlokadyGlobalnej>) polaczenie -> {
                    try (CallableStatement procedura = polaczenie.prepareCall(
                            "{call pkg_klienci.zablokuj_we_wszystkich_sieciach(?, ?, ?, ?)}")) {
                        procedura.setLong(1, idKlienta);
                        procedura.registerOutParameter(2, Types.NUMERIC);
                        procedura.registerOutParameter(3, Types.NUMERIC);
                        procedura.registerOutParameter(4, Types.NUMERIC);
                        procedura.execute();
                        Map<String, Integer> zablokowane = new LinkedHashMap<>();
                        zablokowane.put("VISA", procedura.getInt(2));
                        zablokowane.put("MC", procedura.getInt(3));
                        zablokowane.put("AMEX", procedura.getInt(4));
                        return new WynikBlokadyGlobalnej(idKlienta, zablokowane);
                    }
                }));
    }

    private static Klient odczytaj(Wezel wezel, long idKlienta) {
        return wezel.jdbc().sql("SELECT " + Klient.KOLUMNY + " FROM klient WHERE id_klienta = ?")
                .param(idKlienta)
                .query((rs, i) -> Klient.zWiersza(rs))
                .optional()
                .orElseThrow(() -> BladApi.nieZnaleziono("Klient nie istnieje"));
    }

    private static String wzorzec(String szukaj) {
        return szukaj == null ? null : ZapytanieSql.wzorzecLike(szukaj);
    }
}
