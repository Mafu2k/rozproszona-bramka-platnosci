package pl.janicki.bramka.idempotencja;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import pl.janicki.bramka.bledy.BladApi;
import pl.janicki.bramka.uwierzytelnianie.ZalogowanyUzytkownik;
import pl.janicki.bramka.wezly.Wezel;
import pl.janicki.bramka.wspolne.Skrot;
import tools.jackson.databind.json.JsonMapper;

@Component
public class IdempotencjaPanelu {

    public static final String NAGLOWEK = "Idempotency-Key";

    private static final Logger log = LoggerFactory.getLogger(IdempotencjaPanelu.class);
    private static final int MAKS_PROB = 2;
    private static final Pattern FORMAT_KLUCZA = Pattern.compile("^[A-Za-z0-9._:-]{8,64}$");

    public record Odpowiedz<T>(T tresc, boolean powtorzona) {
    }

    private record Zapis(String odcisk, String odpowiedz) {
    }

    private final JsonMapper json;

    public IdempotencjaPanelu(JsonMapper json) {
        this.json = json;
    }

    public <T> Odpowiedz<T> wykonaj(Wezel wezel, ZalogowanyUzytkownik uzytkownik, String klucz, String operacja, Object dane,
            Class<T> typ, Supplier<T> akcja) {
        return wykonaj(wezel, uzytkownik, klucz, operacja, dane, typ, akcja, UnaryOperator.identity());
    }

    public <T> Odpowiedz<T> wykonaj(Wezel wezel, ZalogowanyUzytkownik uzytkownik, String klucz, String operacja, Object dane,
            Class<T> typ, Supplier<T> akcja, UnaryOperator<T> doZapisu) {
        if (klucz == null || !FORMAT_KLUCZA.matcher(klucz).matches()) {
            throw BladApi.walidacja("Nieprawidłowy nagłówek " + NAGLOWEK,
                    Map.of(NAGLOWEK, "8-64 znaki: litery, cyfry oraz . _ : -"));
        }
        String odcisk = Skrot.sha256(operacja + "\n" + json.writeValueAsString(dane == null ? Map.of() : dane));
        for (int proba = 1;; proba++) {
            try {
                return wezel.transakcja().execute(status -> {
                    Optional<Odpowiedz<T>> zapisana = zarezerwuj(wezel, uzytkownik.id(), klucz, odcisk, typ);
                    if (zapisana.isPresent()) {
                        return zapisana.get();
                    }
                    T wynik = akcja.get();
                    T doBazy = wynik == null ? null : doZapisu.apply(wynik);
                    wezel.jdbc().sql("UPDATE zadanie_panelu SET status_http = ?, odpowiedz = ? WHERE id_uzytkownika = ? AND klucz = ?")
                            .params(wynik == null ? 204 : 200, doBazy == null ? null : json.writeValueAsString(doBazy), uzytkownik.id(), klucz)
                            .update();
                    return new Odpowiedz<>(wynik, false);
                });
            } catch (RuntimeException e) {
                if (proba < MAKS_PROB && wezel.bladLaczaDoInnegoWezla(e)) {
                    log.warn("Łącze z węzła {} do innej sieci zerwane, odświeżam połączenia i ponawiam operację", wezel.kodSieci());
                    wezel.odswiezPolaczenia();
                    continue;
                }
                throw wezel.przetlumacz(e);
            }
        }
    }

    private <T> Optional<Odpowiedz<T>> zarezerwuj(Wezel wezel, long idUzytkownika, String klucz, String odcisk, Class<T> typ) {
        try {
            wezel.jdbc().sql("INSERT INTO zadanie_panelu (id_uzytkownika, klucz, odcisk) VALUES (?, ?, ?)")
                    .params(idUzytkownika, klucz, odcisk)
                    .update();
            return Optional.empty();
        } catch (DuplicateKeyException e) {
            Zapis zapis = wezel.jdbc().sql("SELECT odcisk, odpowiedz FROM zadanie_panelu WHERE id_uzytkownika = ? AND klucz = ?")
                    .params(idUzytkownika, klucz)
                    .query((rs, i) -> new Zapis(rs.getString(1), rs.getString(2)))
                    .single();
            if (!zapis.odcisk().equals(odcisk)) {
                throw BladApi.duplikatZadania(klucz);
            }
            T tresc = zapis.odpowiedz() == null ? null : json.readValue(zapis.odpowiedz(), typ);
            return Optional.of(new Odpowiedz<>(tresc, true));
        }
    }
}
