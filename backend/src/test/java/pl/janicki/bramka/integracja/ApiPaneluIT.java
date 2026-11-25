package pl.janicki.bramka.integracja;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApiPaneluIT {

    private static Dane.Sklep sklep;
    private static long klient;
    private static Dane.Karta kartaMc;
    private static Dane.Karta kartaVisa;
    private static String rrn;

    @BeforeAll
    static void przygotuj() {
        Dane.czekajNaBramke();
        sklep = Dane.nowySklep();
        klient = Dane.nowyKlient();
        kartaMc = Dane.nowaKarta("MC", klient, 300_000);
        kartaVisa = Dane.nowaKarta("VISA", klient, 300_000);
        rrn = Dane.autoryzuj(sklep, kartaMc.numer(), Dane.unikalny("panel"), 4_200).tekst("rrn");
    }

    @Test
    @DisplayName("FR1: logowanie, bieżący użytkownik, wylogowanie unieważnia token, błędne hasło 401")
    void logowanie() {
        Api.Odpowiedz ja = Api.get("/auth/ja").token(Dane.operator("MC")).wyslij();
        assertThat(ja.tekst("rola")).isEqualTo("OPERATOR");
        assertThat(ja.tekst("siec")).isEqualTo("MC");

        String token = Api.post("/auth/logowanie")
                .tresc(Map.of("login", "admin", "haslo", Srodowisko.zmienna("PANEL_ADMIN_PASSWORD"))).wyslij().tekst("token");
        assertThat(Api.post("/auth/wylogowanie").token(token).wyslij().status()).isEqualTo(204);
        assertThat(Api.get("/auth/ja").token(token).wyslij().status()).isEqualTo(401);
        assertThat(Api.post("/auth/logowanie").tresc(Map.of("login", "admin", "haslo", "zle-haslo")).wyslij().status()).isEqualTo(401);
        assertThat(Api.get("/klienci").wyslij().status()).isEqualTo(401);
    }

    @Test
    @DisplayName("FR4: klienci - lista z filtrem, szczegóły, edycja, karty z wielu sieci, usunięcie logiczne")
    void klienci() {
        Api.Odpowiedz lista = Api.get("/klienci?szukaj=Integracyjny&rozmiar=5").token(Dane.admin()).wyslij();
        assertThat(lista.status()).isEqualTo(200);
        assertThat(lista.liczba("lacznie")).isPositive();

        Api.Odpowiedz szczegoly = Api.get("/klienci/" + klient).token(Dane.operator("VISA")).wyslij();
        assertThat(szczegoly.status()).isEqualTo(200);

        Api.Odpowiedz karty = Api.get("/klienci/" + klient + "/karty").token(Dane.admin()).wyslij();
        assertThat(karty.tresc().get("elementy")).hasSize(2);
        Api.Odpowiedz kartyOperatora = Api.get("/klienci/" + klient + "/karty").token(Dane.operator("MC")).wyslij();
        assertThat(kartyOperatora.tresc().get("elementy")).hasSize(1);

        long doUsuniecia = Dane.nowyKlient();
        assertThat(Api.delete("/klienci/" + doUsuniecia).token(Dane.admin()).idempotencja(Dane.unikalny("usun")).wyslij().status())
                .isEqualTo(204);
        assertThat(Api.get("/klienci/" + doUsuniecia).token(Dane.admin()).wyslij().tekst("status")).isEqualTo("USUNIETY");

        Api.Odpowiedz zKartami = Api.delete("/klienci/" + klient).token(Dane.admin()).idempotencja(Dane.unikalny("usun")).wyslij();
        assertThat(zKartami.status()).isEqualTo(409);
        assertThat(zKartami.tekst("kod")).isEqualTo("KLIENT_MA_AKTYWNE_KARTY");
        assertThat(Api.get("/klienci/999999999").token(Dane.admin()).wyslij().status()).isEqualTo(404);
    }

    @Test
    @DisplayName("FR5: sklepy - dodanie, edycja, zawieszenie wyłącza klucz API, usunięcie")
    void sklepy() {
        Dane.Sklep nowy = Dane.nowySklep();
        assertThat(Api.get("/sklepy/" + nowy.id()).token(Dane.admin()).wyslij().tresc().get("maKluczApi").asBoolean()).isTrue();
        assertThat(Api.get("/sklepy?szukaj=Sklep&rozmiar=3").token(Dane.admin()).wyslij().status()).isEqualTo(200);

        Api.Odpowiedz zawieszenie = Api.put("/sklepy/" + nowy.id()).token(Dane.admin()).idempotencja(Dane.unikalny("zaw"))
                .tresc(Map.of("nazwa", "Zawieszony", "nip", Dane.nip(), "kategoriaMcc", "5999",
                        "emailKontaktowy", "zawieszony@example.com", "status", "ZAWIESZONY"))
                .wyslij();
        assertThat(zawieszenie.status()).isEqualTo(200);
        assertThat(Dane.autoryzuj(nowy, kartaMc.numer(), Dane.unikalny("zaw"), 1).status()).isEqualTo(403);

        assertThat(Api.delete("/sklepy/" + nowy.id()).token(Dane.admin()).idempotencja(Dane.unikalny("usun")).wyslij().status())
                .isEqualTo(204);
        assertThat(Dane.autoryzuj(nowy, kartaMc.numer(), Dane.unikalny("usun"), 1).status()).isEqualTo(401);

        Api.Odpowiedz zlyNip = Api.post("/sklepy").token(Dane.admin()).idempotencja(Dane.unikalny("nip"))
                .tresc(Map.of("nazwa", "X", "nip", "1234567890", "kategoriaMcc", "5999", "emailKontaktowy", "x@example.com"))
                .wyslij();
        assertThat(zlyNip.status()).isEqualTo(400);
        assertThat(zlyNip.tekst("kod")).isEqualTo("WALIDACJA");
    }

    @Test
    @DisplayName("FR6: karty - lista, limit, blokada, odblokowanie, limit poniżej wykorzystania")
    void karty() {
        String operator = Dane.operator("MC");
        Api.Odpowiedz lista = Api.get("/karty?idKlienta=" + klient).token(operator).wyslij();
        assertThat(lista.tresc().get("elementy")).hasSize(1);
        Api.Odpowiedz wszystkie = Api.get("/karty?idKlienta=" + klient).token(Dane.admin()).wyslij();
        assertThat(wszystkie.tresc().get("elementy")).hasSize(2);

        Api.Odpowiedz limit = Api.put("/karty/" + kartaMc.id() + "/limit").token(operator).idempotencja(Dane.unikalny("limit"))
                .tresc(Map.of("limitKredytowy", 400_000)).wyslij();
        assertThat(limit.liczba("limitKredytowy")).isEqualTo(400_000);
        Api.Odpowiedz zaNiski = Api.put("/karty/" + kartaMc.id() + "/limit").token(operator).idempotencja(Dane.unikalny("limit"))
                .tresc(Map.of("limitKredytowy", 1)).wyslij();
        assertThat(zaNiski.status()).isEqualTo(409);
        assertThat(zaNiski.tekst("kod")).isEqualTo("LIMIT_PONIZEJ_WYKORZYSTANIA");

        assertThat(Api.post("/karty/" + kartaMc.id() + "/blokada").token(operator).idempotencja(Dane.unikalny("b")).wyslij()
                .tekst("status")).isEqualTo("ZABLOKOWANA");
        assertThat(Dane.autoryzuj(sklep, kartaMc.numer(), Dane.unikalny("blok"), 100).tekst("kod")).isEqualTo("62");
        assertThat(Api.post("/karty/" + kartaMc.id() + "/odblokowanie").token(operator).idempotencja(Dane.unikalny("o")).wyslij()
                .tekst("status")).isEqualTo("AKTYWNA");
    }

    @Test
    @DisplayName("Uprawnienia sprawdzane w backendzie na poziomie obiektu")
    void uprawnienia() {
        assertThat(Api.get("/karty/" + kartaVisa.id()).token(Dane.operator("MC")).wyslij().status()).isEqualTo(403);
        assertThat(Api.get("/transakcje?siec=VISA").token(Dane.operator("MC")).wyslij().status()).isEqualTo(403);
        assertThat(Api.get("/transakcje?idKarty=" + kartaVisa.id()).token(Dane.operator("MC")).wyslij().status()).isEqualTo(403);
        assertThat(Api.post("/karty/" + kartaVisa.id() + "/blokada").token(Dane.operator("MC")).idempotencja(Dane.unikalny("x")).wyslij()
                .status()).isEqualTo(403);
        assertThat(Api.post("/klienci").token(Dane.operator("MC")).idempotencja(Dane.unikalny("x"))
                .tresc(Map.of("imie", "A", "nazwisko", "B", "email", "ab@example.com", "kraj", "PL")).wyslij().status()).isEqualTo(403);
        assertThat(Api.post("/karty").token(Dane.admin()).idempotencja(Dane.unikalny("x"))
                .tresc(Map.of("idKlienta", klient, "limitKredytowy", 1)).wyslij().status()).isEqualTo(403);
        assertThat(Api.get("/raporty/statystyki-sieci?od=2026-01-01T00:00:00Z&do=2027-01-01T00:00:00Z").token(Dane.operator("VISA"))
                .wyslij().status()).isEqualTo(403);
        assertThat(Api.get("/wezly/stan").token(Dane.operator("AMEX")).wyslij().status()).isEqualTo(403);
    }

    @Test
    @DisplayName("Idempotencja zapisów panelu: ten sam klucz i dane - ta sama odpowiedź; inne dane - 409 z kodem 94")
    void idempotencjaPanelu() {
        String klucz = Dane.unikalny("idem");
        Map<String, Object> dane = Map.of("imie", "Idem", "nazwisko", "Potent", "email", Dane.unikalny("idem") + "@example.com", "kraj", "PL");

        Api.Odpowiedz pierwsza = Api.post("/klienci").token(Dane.admin()).idempotencja(klucz).tresc(dane).wyslij();
        Api.Odpowiedz druga = Api.post("/klienci").token(Dane.admin()).idempotencja(klucz).tresc(dane).wyslij();
        Api.Odpowiedz inne = Api.post("/klienci").token(Dane.admin()).idempotencja(klucz)
                .tresc(Map.of("imie", "Inne", "nazwisko", "Dane", "email", "inne@example.com", "kraj", "PL")).wyslij();
        Api.Odpowiedz bezKlucza = Api.post("/klienci").token(Dane.admin()).tresc(dane).wyslij();

        assertThat(druga.liczba("id")).isEqualTo(pierwsza.liczba("id"));
        assertThat(inne.status()).isEqualTo(409);
        assertThat(inne.tekst("kod")).isEqualTo("94");
        assertThat(bezKlucza.status()).isEqualTo(400);
        assertThat(bezKlucza.tekst("kod")).isEqualTo("BRAK_KLUCZA_IDEMPOTENCJI");

        long innyKlient = Dane.nowyKlient();
        String kluczKarty = Dane.unikalny("karta");
        Api.Odpowiedz[] wydana = new Api.Odpowiedz[1];
        Czekanie.az(java.time.Duration.ofSeconds(90), "replikacja klienta na AmEx", () -> {
            wydana[0] = Api.post("/karty").token(Dane.operator("AMEX")).idempotencja(kluczKarty)
                    .tresc(Map.of("idKlienta", innyKlient, "limitKredytowy", 1000)).wyslij();
            return wydana[0].status() != 409;
        });
        Api.Odpowiedz powtorzona = Api.post("/karty").token(Dane.operator("AMEX")).idempotencja(kluczKarty)
                .tresc(Map.of("idKlienta", innyKlient, "limitKredytowy", 1000)).wyslij();

        assertThat(wydana[0].status()).isEqualTo(201);
        assertThat(wydana[0].tekst("numerKarty")).hasSize(15);
        assertThat(powtorzona.status()).isEqualTo(201);
        assertThat(powtorzona.tekst("numerKarty")).isNull();
        assertThat(powtorzona.tresc().get("karta").get("id").asLong()).isEqualTo(wydana[0].tresc().get("karta").get("id").asLong());
    }

    @Test
    @DisplayName("FR8: transakcje - filtry, sortowanie, stronicowanie, szczegóły ze zwrotami")
    void transakcje() {
        Dane.zwroc(sklep, rrn, Dane.unikalny("zw"), 200);
        Api.Odpowiedz poRrn = Api.get("/transakcje?rrn=" + rrn).token(Dane.admin()).wyslij();
        assertThat(poRrn.tresc().get("elementy")).hasSize(1);
        long id = poRrn.tresc().get("elementy").get(0).get("id").asLong();

        Api.Odpowiedz szczegoly = Api.get("/transakcje/" + id).token(Dane.operator("MC")).wyslij();
        assertThat(szczegoly.tresc().get("zwroty")).hasSize(1);

        Api.Odpowiedz filtr = Api.get("/transakcje?idSklepu=" + sklep.id() + "&typ=AUTORYZACJA&sortuj=kwota&kierunek=asc&rozmiar=2")
                .token(Dane.admin()).wyslij();
        assertThat(filtr.status()).isEqualTo(200);
        assertThat(filtr.tresc().get("czesciowy").asBoolean()).isFalse();
        assertThat(Api.get("/transakcje?rozmiar=500").token(Dane.admin()).wyslij().status()).isEqualTo(400);
    }

    @Test
    @DisplayName("FR9, FR10, FR11: raporty i stan węzłów")
    void raportyIStan() {
        Api.Odpowiedz obroty = Api.get("/raporty/obroty-sklepu?idSklepu=" + sklep.id() + "&od=2026-01-01T00:00:00Z&do=2030-01-01T00:00:00Z")
                .token(Dane.admin()).wyslij();
        assertThat(obroty.tresc().get("lacznie").get("liczbaAutoryzacji").asLong()).isPositive();
        Api.Odpowiedz statystyki = Api.get("/raporty/statystyki-sieci?od=2026-01-01T00:00:00Z&do=2030-01-01T00:00:00Z")
                .token(Dane.admin()).wyslij();
        assertThat(statystyki.tresc().get("sieci")).hasSize(3);
        assertThat(Api.get("/raporty/statystyki-sieci?od=2027-01-01T00:00:00Z&do=2026-01-01T00:00:00Z").token(Dane.admin())
                .wyslij().status()).isEqualTo(400);

        Api.Odpowiedz stan = Api.get("/wezly/stan").token(Dane.admin()).wyslij();
        assertThat(stan.tresc().get("wezly")).hasSize(3);
        assertThat(stan.tresc().get("wezly").get(0).get("logiMv")).isNotEmpty();
        assertThat(stan.tresc().get("wezly").get(1).get("replikacja").get("przekroczonoLimit").asBoolean()).isFalse();
    }
}
