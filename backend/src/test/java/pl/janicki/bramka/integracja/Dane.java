package pl.janicki.bramka.integracja;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class Dane {

    record Sklep(long id, String kluczApi) {
    }

    record Karta(long id, String numer, String siec) {
    }

    private record Token(String wartosc, Instant wygasa) {
    }

    private static final SecureRandom LOSOWANIE = new SecureRandom();
    private static final Map<String, Token> TOKENY = new ConcurrentHashMap<>();
    private static final Duration LIMIT_REPLIKACJI = Duration.ofSeconds(90);

    private Dane() {
    }

    static String admin() {
        return token("admin", Srodowisko.zmienna("PANEL_ADMIN_PASSWORD"));
    }

    static String operator(String siec) {
        String login = switch (siec) {
            case "VISA" -> "operator.visa";
            case "MC" -> "operator.mastercard";
            case "AMEX" -> "operator.amex";
            default -> throw new IllegalArgumentException(siec);
        };
        return token(login, Srodowisko.zmienna("PANEL_OPERATOR_PASSWORD"));
    }

    static String token(String login, String haslo) {
        Token zapamietany = TOKENY.get(login);
        if (zapamietany != null && zapamietany.wygasa().isAfter(Instant.now())) {
            return zapamietany.wartosc();
        }
        Api.Odpowiedz odpowiedz = Api.post("/auth/logowanie").tresc(Map.of("login", login, "haslo", haslo)).wyslij();
        assertThat(odpowiedz.status()).as("logowanie %s: %s", login, odpowiedz).isEqualTo(200);
        String token = odpowiedz.tekst("token");
        TOKENY.put(login, new Token(token, Instant.now().plus(Duration.ofMinutes(20))));
        return token;
    }

    static void zapomnijTokeny() {
        TOKENY.clear();
    }

    static String unikalny(String prefiks) {
        return prefiks + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    static String nip() {
        int[] wagi = { 6, 5, 7, 2, 3, 4, 5, 6, 7 };
        while (true) {
            StringBuilder nip = new StringBuilder();
            int suma = 0;
            for (int i = 0; i < 9; i++) {
                int cyfra = i == 0 ? 1 + LOSOWANIE.nextInt(9) : LOSOWANIE.nextInt(10);
                nip.append(cyfra);
                suma += wagi[i] * cyfra;
            }
            if (suma % 11 != 10) {
                return nip.append(suma % 11).toString();
            }
        }
    }

    static long nowyKlient() {
        Api.Odpowiedz odpowiedz = Api.post("/klienci").token(admin()).idempotencja(unikalny("klient"))
                .tresc(Map.of("imie", "Test", "nazwisko", "Integracyjny", "email", unikalny("test") + "@example.com",
                        "telefon", "+48600000000", "kraj", "PL"))
                .wyslij();
        assertThat(odpowiedz.status()).as("nowy klient: %s", odpowiedz).isEqualTo(201);
        return odpowiedz.liczba("id");
    }

    static Sklep nowySklep() {
        Api.Odpowiedz sklep = Api.post("/sklepy").token(admin()).idempotencja(unikalny("sklep"))
                .tresc(Map.of("nazwa", "Sklep " + unikalny("it"), "nip", nip(), "kategoriaMcc", "5999",
                        "emailKontaktowy", unikalny("sklep") + "@example.com"))
                .wyslij();
        assertThat(sklep.status()).as("nowy sklep: %s", sklep).isEqualTo(201);
        long id = sklep.liczba("id");
        Api.Odpowiedz klucz = Api.post("/sklepy/" + id + "/klucz-api").token(admin()).idempotencja(unikalny("klucz")).wyslij();
        assertThat(klucz.status()).as("klucz API: %s", klucz).isEqualTo(201);
        return new Sklep(id, klucz.tekst("kluczApi"));
    }

    static Karta nowaKarta(String siec, long idKlienta, long limit) {
        String idempotencja = unikalny("karta");
        Api.Odpowiedz[] wynik = new Api.Odpowiedz[1];
        Czekanie.az(LIMIT_REPLIKACJI, "replikacja klienta " + idKlienta + " na " + siec, () -> {
            wynik[0] = Api.post("/karty").token(operator(siec)).idempotencja(idempotencja)
                    .tresc(Map.of("idKlienta", idKlienta, "limitKredytowy", limit)).wyslij();
            return wynik[0].status() != 409 || !"REPLIKACJA_W_TOKU".equals(wynik[0].tekst("kod"));
        });
        assertThat(wynik[0].status()).as("wydanie karty: %s", wynik[0]).isEqualTo(201);
        return new Karta(wynik[0].tresc().get("karta").get("id").asLong(), wynik[0].tekst("numerKarty"), siec);
    }

    static Api.Odpowiedz autoryzuj(Sklep sklep, String numerKarty, String idZadania, long kwota) {
        Api.Odpowiedz[] wynik = new Api.Odpowiedz[1];
        Czekanie.az(LIMIT_REPLIKACJI, "replikacja sklepu " + sklep.id(), () -> {
            wynik[0] = Api.post("/platnosci/autoryzacje").kluczApi(sklep.kluczApi())
                    .tresc(Map.of("idZadania", idZadania, "numerKarty", numerKarty, "kwota", kwota, "waluta", "PLN")).wyslij();
            return wynik[0].status() != 409 || !"REPLIKACJA_W_TOKU".equals(wynik[0].tekst("kod"));
        });
        return wynik[0];
    }

    static Api.Odpowiedz zwroc(Sklep sklep, String rrnPierwotnej, String idZadania, long kwota) {
        return Api.post("/platnosci/zwroty").kluczApi(sklep.kluczApi())
                .tresc(Map.of("idZadania", idZadania, "rrnPierwotnej", rrnPierwotnej, "kwota", kwota, "waluta", "PLN")).wyslij();
    }

    static long dostepneSrodki(Karta karta) {
        Api.Odpowiedz odpowiedz = Api.get("/karty/" + karta.id()).token(operator(karta.siec())).wyslij();
        assertThat(odpowiedz.status()).isEqualTo(200);
        return odpowiedz.liczba("dostepneSrodki");
    }

    static void czekajNaBramke() {
        Czekanie.az(Duration.ofMinutes(3), "bramka loguje administratora", () -> {
            zapomnijTokeny();
            return Api.post("/auth/logowanie").tresc(Map.of("login", "admin", "haslo", Srodowisko.zmienna("PANEL_ADMIN_PASSWORD")))
                    .wyslij().status() == 200;
        });
    }
}
