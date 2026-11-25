package pl.janicki.bramka.bledy;

import java.util.Map;

public record Blad(String kod, String komunikat, Map<String, Object> szczegoly) {
}
