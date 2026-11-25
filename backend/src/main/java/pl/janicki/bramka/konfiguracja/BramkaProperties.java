package pl.janicki.bramka.konfiguracja;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("bramka")
public record BramkaProperties(
        Baza baza,
        List<WezelProperties> wezly,
        Bezpieczenstwo bezpieczenstwo,
        Wydawanie wydawanie,
        UzytkownicyStartowi uzytkownicyStartowi,
        Iso8583 iso8583,
        boolean daneDemo) {

    public record Baza(String uzytkownik, String haslo, int rozmiarPuli, Duration czasPolaczenia, Duration czasZapytania) {
    }

    public record WezelProperties(int nr, String kod, String url) {
    }

    public record Bezpieczenstwo(
            String kluczHmacKarty,
            String sekretJwt,
            Duration waznoscTokenu,
            List<String> dozwoloneZrodlaCors) {
    }

    public record Wydawanie(Map<String, String> biny) {
    }

    public record UzytkownicyStartowi(String hasloAdmina, String hasloOperatorow) {
    }

    public record Iso8583(boolean wlaczony, int port) {
    }
}
