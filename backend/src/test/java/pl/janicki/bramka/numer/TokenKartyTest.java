package pl.janicki.bramka.numer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TokenKartyTest {

    private static byte[] klucz(int pierwszyBajt) {
        byte[] klucz = new byte[32];
        for (int i = 0; i < klucz.length; i++) {
            klucz[i] = (byte) (pierwszyBajt + i);
        }
        return klucz;
    }

    @Test
    void wyliczaHmacSha256ZgodnyZWektoremWzorcowym() {
        TokenKarty token = new TokenKarty(klucz(1));
        assertThat(token.token(NumerKarty.z("4111111111111111")))
                .isEqualTo("b87879844df0599e951e530e7bdf381d7af919faf07fbde3b7aeeb6581dc5ad2");
    }

    @Test
    void innyKluczDajeInnyToken() {
        assertThat(new TokenKarty(klucz(2)).token(NumerKarty.z("4111111111111111")))
                .isEqualTo("2eafe478d5406cdb382f62333d9066c0c3fe53ebc9dd322a94bcea99864a2f3c");
    }

    @Test
    void tokenJestDeterministycznyINieZawieraNumeru() {
        TokenKarty token = new TokenKarty(klucz(1));
        String pierwszy = token.token(NumerKarty.z("5555555555554444"));
        assertThat(token.token(NumerKarty.z("5555555555554444"))).isEqualTo(pierwszy);
        assertThat(pierwszy).hasSize(64).doesNotContain("5555555555554444");
    }

    @Test
    void odrzucaZbytKrotkiKlucz() {
        assertThatThrownBy(() -> new TokenKarty(new byte[16])).isInstanceOf(IllegalStateException.class);
    }
}
