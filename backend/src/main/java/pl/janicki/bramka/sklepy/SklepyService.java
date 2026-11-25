package pl.janicki.bramka.sklepy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pl.janicki.bramka.bledy.BladApi;
import pl.janicki.bramka.idempotencja.IdempotencjaPanelu;
import pl.janicki.bramka.uwierzytelnianie.KluczApi;
import pl.janicki.bramka.uwierzytelnianie.RejestrSklepow;
import pl.janicki.bramka.uwierzytelnianie.ZalogowanyUzytkownik;
import pl.janicki.bramka.wezly.OdczytWspolny;
import pl.janicki.bramka.wezly.Wezel;
import pl.janicki.bramka.wezly.Wezly;
import pl.janicki.bramka.wspolne.Strona;
import pl.janicki.bramka.wspolne.Stronicowanie;
import pl.janicki.bramka.wspolne.ZapytanieSql;

@Service
public class SklepyService {

    public record SklepDane(
            @NotBlank @Size(max = 100) String nazwa,
            @NotBlank @Nip String nip,
            @NotBlank @Pattern(regexp = "^[0-9]{4}$") String kategoriaMcc,
            @Size(max = 200) @Pattern(regexp = "^https?://.+") String url,
            @NotBlank @Email @Size(max = 120) String emailKontaktowy,
            @Pattern(regexp = "AKTYWNY|ZAWIESZONY") String status) {
    }

    public record NowyKluczApi(long idSklepu, String kluczApi) {
    }

    private record Stron(List<Sklep> elementy, long lacznie) {
    }

    private final Wezly wezly;
    private final IdempotencjaPanelu idempotencja;
    private final RejestrSklepow rejestrSklepow;

    public SklepyService(Wezly wezly, IdempotencjaPanelu idempotencja, RejestrSklepow rejestrSklepow) {
        this.wezly = wezly;
        this.idempotencja = idempotencja;
        this.rejestrSklepow = rejestrSklepow;
    }

    public Strona<Sklep> lista(String szukaj, String status, Stronicowanie stronicowanie) {
        String wzorzec = szukaj == null || szukaj.isBlank() ? null : ZapytanieSql.wzorzecLike(szukaj);
        ZapytanieSql warunki = new ZapytanieSql()
                .warunek(wzorzec != null, "(UPPER(nazwa) LIKE ? ESCAPE '\\' OR nip LIKE ? ESCAPE '\\')", wzorzec, wzorzec)
                .warunek(status != null, "status = ?", status);
        OdczytWspolny<Stron> odczyt = wezly.czytajDaneWspolne(wezel -> new Stron(
                wezel.jdbc().sql("SELECT " + Sklep.KOLUMNY + " FROM sklep" + warunki.where()
                        + " ORDER BY nazwa, id_sklepu OFFSET " + stronicowanie.przesuniecie()
                        + " ROWS FETCH NEXT " + stronicowanie.rozmiar() + " ROWS ONLY")
                        .params(warunki.parametry()).query((rs, i) -> Sklep.zWiersza(rs)).list(),
                wezel.jdbc().sql("SELECT COUNT(*) FROM sklep" + warunki.where())
                        .params(warunki.parametry()).query(Long.class).single()));
        return Strona.zJednegoZrodla(odczyt.wartosc().elementy(), stronicowanie.strona(), stronicowanie.rozmiar(),
                odczyt.wartosc().lacznie(), odczyt.zrodlo());
    }

    public Sklep pobierz(long idSklepu) {
        return wezly.czytajDaneWspolne(wezel -> odczytaj(wezel, idSklepu)).wartosc();
    }

    public IdempotencjaPanelu.Odpowiedz<Sklep> dodaj(SklepDane dane, String klucz, ZalogowanyUzytkownik uzytkownik) {
        Wezel glowny = wezly.glowny();
        return idempotencja.wykonaj(glowny, uzytkownik, klucz, "POST /sklepy", dane, Sklep.class, () -> {
            KeyHolder klucze = new GeneratedKeyHolder();
            glowny.jdbc().sql("INSERT INTO sklep (nazwa, nip, kategoria_mcc, url, email_kontaktowy) VALUES (?, ?, ?, ?, ?)")
                    .params(dane.nazwa().trim(), dane.nip(), dane.kategoriaMcc(), dane.url(), dane.emailKontaktowy().trim().toLowerCase())
                    .update(klucze, "id_sklepu");
            return odczytaj(glowny, klucze.getKeyAs(BigDecimal.class).longValue());
        });
    }

    public IdempotencjaPanelu.Odpowiedz<Sklep> zmien(long idSklepu, SklepDane dane, String klucz, ZalogowanyUzytkownik uzytkownik) {
        if (dane.status() == null) {
            throw BladApi.walidacja("Nieprawidłowe dane żądania", Map.of("status", "wymagany: AKTYWNY albo ZAWIESZONY"));
        }
        Wezel glowny = wezly.glowny();
        IdempotencjaPanelu.Odpowiedz<Sklep> wynik = idempotencja.wykonaj(glowny, uzytkownik, klucz, "PUT /sklepy/" + idSklepu, dane,
                Sklep.class, () -> {
                    int zmienione = glowny.jdbc().sql("""
                            UPDATE sklep SET nazwa = ?, nip = ?, kategoria_mcc = ?, url = ?, email_kontaktowy = ?, status = ?
                             WHERE id_sklepu = ? AND status <> 'USUNIETY'
                            """)
                            .params(dane.nazwa().trim(), dane.nip(), dane.kategoriaMcc(), dane.url(),
                                    dane.emailKontaktowy().trim().toLowerCase(), dane.status(), idSklepu)
                            .update();
                    if (zmienione == 0) {
                        throw BladApi.nieZnaleziono("Sklep nie istnieje");
                    }
                    return odczytaj(glowny, idSklepu);
                });
        rejestrSklepow.wyczysc();
        return wynik;
    }

    public void usun(long idSklepu, String klucz, ZalogowanyUzytkownik uzytkownik) {
        Wezel glowny = wezly.glowny();
        idempotencja.wykonaj(glowny, uzytkownik, klucz, "DELETE /sklepy/" + idSklepu, null, Void.class, () -> {
            int zmienione = glowny.jdbc().sql("UPDATE sklep SET status = 'USUNIETY', hash_klucza_api = NULL WHERE id_sklepu = ?")
                    .param(idSklepu).update();
            if (zmienione == 0) {
                throw BladApi.nieZnaleziono("Sklep nie istnieje");
            }
            return null;
        });
        rejestrSklepow.wyczysc();
    }

    public IdempotencjaPanelu.Odpowiedz<NowyKluczApi> nowyKluczApi(long idSklepu, String klucz, ZalogowanyUzytkownik uzytkownik) {
        Wezel glowny = wezly.glowny();
        IdempotencjaPanelu.Odpowiedz<NowyKluczApi> wynik = idempotencja.wykonaj(glowny, uzytkownik, klucz,
                "POST /sklepy/" + idSklepu + "/klucz-api", null, NowyKluczApi.class, () -> {
                    String kluczApi = KluczApi.nowy();
                    int zmienione = glowny.jdbc().sql("UPDATE sklep SET hash_klucza_api = ? WHERE id_sklepu = ? AND status <> 'USUNIETY'")
                            .params(KluczApi.skrot(kluczApi), idSklepu).update();
                    if (zmienione == 0) {
                        throw BladApi.nieZnaleziono("Sklep nie istnieje");
                    }
                    return new NowyKluczApi(idSklepu, kluczApi);
                }, nowy -> new NowyKluczApi(nowy.idSklepu(), null));
        rejestrSklepow.wyczysc();
        return wynik;
    }

    private static Sklep odczytaj(Wezel wezel, long idSklepu) {
        return wezel.jdbc().sql("SELECT " + Sklep.KOLUMNY + " FROM sklep WHERE id_sklepu = ?")
                .param(idSklepu)
                .query((rs, i) -> Sklep.zWiersza(rs))
                .optional()
                .orElseThrow(() -> BladApi.nieZnaleziono("Sklep nie istnieje"));
    }
}
