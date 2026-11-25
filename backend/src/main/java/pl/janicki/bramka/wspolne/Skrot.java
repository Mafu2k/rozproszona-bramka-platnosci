package pl.janicki.bramka.wspolne;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class Skrot {

    private Skrot() {
    }

    public static String sha256(String tekst) {
        try {
            MessageDigest skrot = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(skrot.digest(tekst.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
