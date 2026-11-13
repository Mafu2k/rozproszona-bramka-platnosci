package pl.janicki.bramka.integracja;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class Api {

    record Odpowiedz(int status, JsonNode tresc, Map<String, List<String>> naglowki) {

        String tekst(String pole) {
            JsonNode wartosc = tresc.get(pole);
            return wartosc == null || wartosc.isNull() ? null : wartosc.asString();
        }

        long liczba(String pole) {
            return tresc.get(pole).asLong();
        }

        String naglowek(String nazwa) {
            return naglowki.entrySet().stream()
                    .filter(e -> e.getKey().equalsIgnoreCase(nazwa))
                    .flatMap(e -> e.getValue().stream())
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public String toString() {
            return "HTTP " + status + " " + tresc;
        }
    }

    static final JsonMapper JSON = JsonMapper.builder().build();

    private static final HttpClient KLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final OpenApiInteractionValidator KONTRAKT = OpenApiInteractionValidator
            .createForSpecificationUrl(Srodowisko.KATALOG_PROJEKTU.resolve("api").resolve("openapi.yaml").toUri().toString())
            .withBasePathOverride("/api/v1")
            .withResolveCombinators(true)
            .build();

    private final String metoda;
    private final String sciezka;
    private final Map<String, String> naglowki = new LinkedHashMap<>();
    private Object tresc;

    private Api(String metoda, String sciezka) {
        this.metoda = metoda;
        this.sciezka = sciezka;
    }

    static Api get(String sciezka) {
        return new Api("GET", sciezka);
    }

    static Api post(String sciezka) {
        return new Api("POST", sciezka);
    }

    static Api put(String sciezka) {
        return new Api("PUT", sciezka);
    }

    static Api delete(String sciezka) {
        return new Api("DELETE", sciezka);
    }

    Api token(String token) {
        naglowki.put("Authorization", "Bearer " + token);
        return this;
    }

    Api kluczApi(String klucz) {
        naglowki.put("X-Api-Key", klucz);
        return this;
    }

    Api idempotencja(String klucz) {
        naglowki.put("Idempotency-Key", klucz);
        return this;
    }

    Api tresc(Object tresc) {
        this.tresc = tresc;
        return this;
    }

    Odpowiedz wyslij() {
        String cialo = tresc == null ? null : JSON.writeValueAsString(tresc);
        HttpRequest.Builder zadanie = HttpRequest.newBuilder(URI.create(Srodowisko.adresBramki() + "/api/v1" + sciezka))
                .timeout(Duration.ofSeconds(30))
                .method(metoda, cialo == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(cialo));
        if (cialo != null) {
            zadanie.header("Content-Type", "application/json");
        }
        naglowki.forEach(zadanie::header);
        try {
            HttpResponse<String> odpowiedz = KLIENT.send(zadanie.build(), HttpResponse.BodyHandlers.ofString());
            sprawdzKontrakt(odpowiedz);
            JsonNode json = odpowiedz.body() == null || odpowiedz.body().isBlank() ? null : JSON.readTree(odpowiedz.body());
            return new Odpowiedz(odpowiedz.statusCode(), json, odpowiedz.headers().map());
        } catch (IOException e) {
            throw new IllegalStateException("Brak połączenia z bramką " + Srodowisko.adresBramki(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void sprawdzKontrakt(HttpResponse<String> odpowiedz) {
        String sciezkaBezZapytania = sciezka.contains("?") ? sciezka.substring(0, sciezka.indexOf('?')) : sciezka;
        SimpleResponse.Builder budowa = SimpleResponse.Builder.status(odpowiedz.statusCode());
        odpowiedz.headers().firstValue("Content-Type").ifPresent(typ -> budowa.withContentType(typ));
        if (odpowiedz.body() != null && !odpowiedz.body().isBlank()) {
            budowa.withBody(odpowiedz.body());
        }
        ValidationReport raport = KONTRAKT.validateResponse("/api/v1" + sciezkaBezZapytania, Request.Method.valueOf(metoda), budowa.build());
        List<ValidationReport.Message> bledy = raport.getMessages().stream()
                .filter(m -> m.getLevel() == ValidationReport.Level.ERROR)
                .toList();
        if (!bledy.isEmpty()) {
            throw new AssertionError("Odpowiedź " + metoda + " " + sciezka + " (HTTP " + odpowiedz.statusCode()
                    + ") niezgodna z openapi.yaml:\n" + bledy.stream().map(ValidationReport.Message::toString).collect(Collectors.joining("\n"))
                    + "\nTreść: " + odpowiedz.body());
        }
    }
}
