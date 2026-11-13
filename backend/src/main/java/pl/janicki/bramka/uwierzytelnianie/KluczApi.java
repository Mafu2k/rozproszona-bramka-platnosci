package pl.janicki.bramka.uwierzytelnianie;

import java.security.SecureRandom;
import java.util.Base64;

import pl.janicki.bramka.wspolne.Skrot;

public final class KluczApi {

    private static final String PREFIKS = "bp_";
    private static final SecureRandom LOSOWANIE = new SecureRandom();

    private KluczApi() {
    }

    public static String nowy() {
        byte[] bajty = new byte[32];
        LOSOWANIE.nextBytes(bajty);
        return PREFIKS + Base64.getUrlEncoder().withoutPadding().encodeToString(bajty);
    }

    public static String skrot(String klucz) {
        return Skrot.sha256(klucz);
    }
}
