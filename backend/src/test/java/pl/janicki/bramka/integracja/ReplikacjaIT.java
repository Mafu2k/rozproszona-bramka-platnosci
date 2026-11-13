package pl.janicki.bramka.integracja;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReplikacjaIT {

    @Test
    @DisplayName("6 / NFR5 (6.3): zmiana klienta na węźle 1 widoczna na węzłach 2 i 3 w ciągu 60 s")
    void zmianaKlientaWidocznaNaReplikach() {
        Dane.czekajNaBramke();
        long idKlienta = Dane.nowyKlient();
        String telefon = "+48" + (500_000_000 + (System.nanoTime() % 99_999_999));

        Api.Odpowiedz zmiana = Api.put("/klienci/" + idKlienta).token(Dane.admin()).idempotencja(Dane.unikalny("zmiana"))
                .tresc(Map.of("imie", "Zmieniony", "nazwisko", "Replikowany", "email", Dane.unikalny("repl") + "@example.com",
                        "telefon", telefon, "kraj", "PL", "status", "AKTYWNY"))
                .wyslij();
        assertThat(zmiana.status()).isEqualTo(200);
        long start = System.nanoTime();

        for (String siec : new String[] { "MC", "AMEX" }) {
            Czekanie.az(Duration.ofSeconds(60).minusNanos(System.nanoTime() - start), "zmiana klienta na " + siec,
                    () -> telefon.equals(Bazy.wartosc(siec, "SELECT telefon FROM klient WHERE id_klienta = ?", idKlienta)));
            System.out.printf("NFR5: zmiana widoczna na %s po %d ms%n", siec, (System.nanoTime() - start) / 1_000_000);
        }
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThanOrEqualTo(Duration.ofSeconds(60));
    }
}
