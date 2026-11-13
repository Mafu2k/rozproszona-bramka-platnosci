package pl.janicki.bramka.demonstracja;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class KlientBramki {

    record Odpowiedz(int status, JsonNode tresc) {

        String tekst(String pole) {
            JsonNode wartosc = tresc == null ? null : tresc.get(pole);
            return wartosc == null || wartosc.isNull() ? null : wartosc.asString();
        }

        boolean replikacjaWToku() {
            return status == 409 && "REPLIKACJA_W_TOKU".equals(tekst("kod"));
        }
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Duration CZAS_ODPOWIEDZI = Duration.ofSeconds(15);
    private static final Duration PRZERWA = Duration.ofSeconds(3);

    private final String adres;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    KlientBramki(String adres) {
        this.adres = adres;
    }

    Odpowiedz wyslij(String metoda, String sciezka, Map<String, String> naglowki, Object tresc) {
        String cialo = tresc == null ? null : JSON.writeValueAsString(tresc);
        HttpRequest.Builder zadanie = HttpRequest.newBuilder(URI.create(adres + "/api/v1" + sciezka))
                .timeout(CZAS_ODPOWIEDZI)
                .method(metoda, cialo == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(cialo));
        if (cialo != null) {
            zadanie.header("Content-Type", "application/json");
        }
        naglowki.forEach(zadanie::header);
        try {
            HttpResponse<String> odpowiedz = http.send(zadanie.build(), HttpResponse.BodyHandlers.ofString());
            String body = odpowiedz.body();
            return new Odpowiedz(odpowiedz.statusCode(), body == null || body.isBlank() ? null : JSON.readTree(body));
        } catch (IOException e) {
            throw new UncheckedIOException("Brak połączenia z bramką " + adres, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    String zaloguj(String login, String haslo) {
        Odpowiedz odpowiedz = wyslij("POST", "/auth/logowanie", Map.of(), Map.of("login", login, "haslo", haslo));
        if (odpowiedz.status() != 200) {
            throw new IllegalStateException("Logowanie " + login + " nieudane: HTTP " + odpowiedz.status());
        }
        return odpowiedz.tekst("token");
    }

    void czekajNaGotowosc(String hasloAdmina, Duration limit, PrintStream wyjscie) {
        Instant koniec = Instant.now().plus(limit);
        Instant nastepnyKomunikat = Instant.EPOCH;
        while (Instant.now().isBefore(koniec)) {
            try {
                zaloguj("admin", hasloAdmina);
                return;
            } catch (RuntimeException e) {
                if (Instant.now().isAfter(nastepnyKomunikat)) {
                    wyjscie.println("Czekam na bramkę " + adres + " (" + e.getMessage() + ")");
                    nastepnyKomunikat = Instant.now().plusSeconds(30);
                }
                czekaj(PRZERWA);
            }
        }
        throw new IllegalStateException("Bramka " + adres + " nie odpowiada od " + limit.toMinutes() + " min");
    }

    Odpowiedz zapisPanelu(String token, String sciezka, Object tresc) {
        Map<String, String> naglowki = new LinkedHashMap<>();
        naglowki.put("Authorization", "Bearer " + token);
        naglowki.put("Idempotency-Key", UUID.randomUUID().toString());
        return wyslij("POST", sciezka, naglowki, tresc);
    }

    Odpowiedz zapisPaneluPoReplikacji(String token, String sciezka, Object tresc, Duration limit) {
        Map<String, String> naglowki = Map.of("Authorization", "Bearer " + token, "Idempotency-Key", UUID.randomUUID().toString());
        Instant koniec = Instant.now().plus(limit);
        while (true) {
            Odpowiedz odpowiedz = wyslij("POST", sciezka, naglowki, tresc);
            if (!odpowiedz.replikacjaWToku() || Instant.now().isAfter(koniec)) {
                return odpowiedz;
            }
            czekaj(PRZERWA);
        }
    }

    Odpowiedz platnosc(String sciezka, String kluczApi, Map<String, Object> tresc) {
        return wyslij("POST", sciezka, Map.of("X-Api-Key", kluczApi), tresc);
    }

    static void czekaj(Duration czas) {
        try {
            Thread.sleep(czas);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
