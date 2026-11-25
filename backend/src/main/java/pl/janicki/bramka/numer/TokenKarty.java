package pl.janicki.bramka.numer;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import pl.janicki.bramka.konfiguracja.BramkaProperties;

@Component
public class TokenKarty {

    private static final String ALGORYTM = "HmacSHA256";
    private static final int MIN_DLUGOSC_KLUCZA = 32;

    private final SecretKeySpec klucz;

    @Autowired
    public TokenKarty(BramkaProperties properties) {
        this(HexFormat.of().parseHex(properties.bezpieczenstwo().kluczHmacKarty()));
    }

    TokenKarty(byte[] klucz) {
        if (klucz.length < MIN_DLUGOSC_KLUCZA) {
            throw new IllegalStateException("Klucz HMAC karty musi mieć co najmniej 32 bajty (64 znaki hex)");
        }
        this.klucz = new SecretKeySpec(klucz, ALGORYTM);
    }

    public String token(NumerKarty numer) {
        try {
            Mac mac = Mac.getInstance(ALGORYTM);
            mac.init(klucz);
            return HexFormat.of().formatHex(mac.doFinal(numer.cyfry().getBytes(StandardCharsets.US_ASCII)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Nie udało się wyliczyć tokenu karty", e);
        }
    }
}
