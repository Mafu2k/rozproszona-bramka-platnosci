package pl.janicki.bramka.integracja;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

class OdlaczenieSieciIT {

    private static final String KLIENCI = "SELECT id_klienta || '|' || email || '|' || telefon || '|' || status FROM klient";

    @Test
    @DisplayName("7: odłączenie sieci węzła 2 w trakcie odświeżania; po powrocie zaległe zmiany są nałożone bez braków i duplikatów")
    void zalegleZmianyNakladanePoPrzywroceniuSieci() {
        Dane.czekajNaBramke();
        List<Long> zmieniani = List.of(Dane.nowyKlient(), Dane.nowyKlient());
        Czekanie.az(Duration.ofSeconds(90), "repliki aktualne przed testem", () -> zbior("MC").equals(zbior("VISA")));

        Docker.odlaczOdSieci("mastercard-db");
        try {
            Czekanie.az(Duration.ofSeconds(30), "FR11 widzi węzeł MC jako niedostępny", () -> !dostepny("MC"));
            List<Long> nowi = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                nowi.add(Dane.nowyKlient());
            }
            for (long id : zmieniani) {
                Api.Odpowiedz zmiana = Api.put("/klienci/" + id).token(Dane.admin()).idempotencja(Dane.unikalny("odl"))
                        .tresc(Map.of("imie", "Po", "nazwisko", "Odlaczeniu", "email", Dane.unikalny("odl") + "@example.com",
                                "telefon", "+48700" + (id % 1_000_000), "kraj", "PL", "status", "ZABLOKOWANY"))
                        .wyslij();
                assertThat(zmiana.status()).isEqualTo(200);
            }
            assertThat(nowi).hasSize(3);
            Czekanie.spij(Duration.ofSeconds(35));
        } finally {
            Docker.podlaczDoSieci("mastercard-db");
        }

        Duration nadrobienie = Czekanie.az(Duration.ofSeconds(120), "MC nadrobił zaległe zmiany",
                () -> zbior("MC").equals(zbior("VISA")));
        System.out.printf("Test 7: replika MC zgodna z węzłem głównym %d s po przywróceniu sieci%n", nadrobienie.toSeconds());
        assertThat(Bazy.liczba("MC", "SELECT COUNT(*) FROM klient"))
                .isEqualTo(Bazy.liczba("MC", "SELECT COUNT(DISTINCT id_klienta) FROM klient"));
        Czekanie.az(Duration.ofSeconds(60), "FR11 widzi MC jako dostępny i aktualny", () -> dostepny("MC") && !przekroczony("MC"));
    }

    private static Set<String> zbior(String siec) {
        Set<String> wiersze = new HashSet<>();
        Bazy.wiersze(siec, KLIENCI).forEach(w -> wiersze.add(String.valueOf(w.getFirst())));
        return wiersze;
    }

    private static JsonNode wezel(String siec) {
        Api.Odpowiedz stan = Api.get("/wezly/stan").token(Dane.admin()).wyslij();
        assertThat(stan.status()).isEqualTo(200);
        for (JsonNode wezel : stan.tresc().get("wezly")) {
            if (siec.equals(wezel.get("siec").asString())) {
                return wezel;
            }
        }
        throw new AssertionError("Brak węzła " + siec + " w FR11");
    }

    private static boolean dostepny(String siec) {
        return wezel(siec).get("dostepny").asBoolean();
    }

    private static boolean przekroczony(String siec) {
        JsonNode replikacja = wezel(siec).get("replikacja");
        return replikacja == null || replikacja.isNull() || replikacja.get("przekroczonoLimit").asBoolean();
    }
}
