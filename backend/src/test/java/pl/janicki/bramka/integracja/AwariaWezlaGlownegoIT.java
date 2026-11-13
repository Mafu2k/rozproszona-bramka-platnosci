package pl.janicki.bramka.integracja;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

class AwariaWezlaGlownegoIT {

    @Test
    @DisplayName("8 / NFR3 (6.4): zatrzymany węzeł 1 - autoryzacje MC i AmEx działają, logowanie działa, zapisy danych wspólnych dają czytelny błąd")
    void awariaWezlaVisa() {
        Dane.czekajNaBramke();
        Dane.Sklep sklep = Dane.nowySklep();
        long klient = Dane.nowyKlient();
        Dane.Karta mc = Dane.nowaKarta("MC", klient, 100_000);
        Dane.Karta amex = Dane.nowaKarta("AMEX", klient, 100_000);
        assertThat(Dane.autoryzuj(sklep, mc.numer(), Dane.unikalny("przed"), 100).tekst("kod")).isEqualTo("00");
        assertThat(Dane.autoryzuj(sklep, amex.numer(), Dane.unikalny("przed"), 100).tekst("kod")).isEqualTo("00");
        String tokenAdmina = Dane.admin();

        Docker.zatrzymaj("visa-db");
        try {
            Api.Odpowiedz autoryzacjaMc = Dane.autoryzuj(sklep, mc.numer(), Dane.unikalny("awaria"), 250);
            Api.Odpowiedz autoryzacjaAmex = Dane.autoryzuj(sklep, amex.numer(), Dane.unikalny("awaria"), 250);
            assertThat(autoryzacjaMc.status()).isEqualTo(200);
            assertThat(autoryzacjaMc.tekst("decyzja")).isEqualTo("ZATWIERDZONA");
            assertThat(autoryzacjaAmex.tekst("decyzja")).isEqualTo("ZATWIERDZONA");

            Dane.zapomnijTokeny();
            Api.Odpowiedz logowanie = Api.post("/auth/logowanie")
                    .tresc(Map.of("login", "operator.mastercard", "haslo", Srodowisko.zmienna("PANEL_OPERATOR_PASSWORD"))).wyslij();
            assertThat(logowanie.status()).isEqualTo(200);

            Api.Odpowiedz klienci = Api.get("/klienci?rozmiar=1").token(tokenAdmina).wyslij();
            assertThat(klienci.status()).isEqualTo(200);
            assertThat(klienci.tekst("zrodlo")).isNotEqualTo("VISA");

            Api.Odpowiedz zapis = Api.post("/klienci").token(tokenAdmina).idempotencja(Dane.unikalny("awaria"))
                    .tresc(Map.of("imie", "Nie", "nazwisko", "Zapisze", "email", Dane.unikalny("x") + "@example.com", "kraj", "PL"))
                    .wyslij();
            assertThat(zapis.status()).isEqualTo(503);
            assertThat(zapis.tekst("kod")).isEqualTo("91");
            assertThat(zapis.tresc().get("szczegoly").get("siec").asString()).isEqualTo("VISA");

            Api.Odpowiedz raport = Api.get("/raporty/obroty-sklepu?idSklepu=" + sklep.id()
                    + "&od=2026-01-01T00:00:00Z&do=2030-01-01T00:00:00Z").token(tokenAdmina).wyslij();
            assertThat(raport.status()).isEqualTo(200);
            assertThat(raport.tresc().get("czesciowy").asBoolean()).isTrue();
            assertThat(raport.tresc().get("brakujaceSieci").get(0).asString()).isEqualTo("VISA");
            assertThat(raport.tresc().get("lacznie").get("liczbaAutoryzacji").asLong()).isGreaterThanOrEqualTo(4);

            Api.Odpowiedz stan = Api.get("/wezly/stan").token(tokenAdmina).wyslij();
            JsonNode visa = stan.tresc().get("wezly").get(0);
            assertThat(visa.get("siec").asString()).isEqualTo("VISA");
            assertThat(visa.get("dostepny").asBoolean()).isFalse();

            Api.Odpowiedz blokada = Api.post("/klienci/" + klient + "/blokada-globalna").token(tokenAdmina)
                    .idempotencja(Dane.unikalny("blok")).wyslij();
            assertThat(blokada.status()).isEqualTo(503);
        } finally {
            Docker.uruchom("visa-db", "VISA");
        }
        Dane.czekajNaBramke();
        Czekanie.az(Duration.ofSeconds(120), "repliki odświeżone po powrocie węzła Visa", () -> {
            Api.Odpowiedz stan = Api.get("/wezly/stan").token(Dane.admin()).wyslij();
            for (JsonNode wezel : stan.tresc().get("wezly")) {
                if (!wezel.get("dostepny").asBoolean()) {
                    return false;
                }
                JsonNode replikacja = wezel.get("replikacja");
                if (replikacja != null && !replikacja.isNull() && replikacja.get("przekroczonoLimit").asBoolean()) {
                    return false;
                }
            }
            return true;
        });
    }
}
