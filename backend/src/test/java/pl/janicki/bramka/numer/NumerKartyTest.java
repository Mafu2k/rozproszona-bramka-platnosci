package pl.janicki.bramka.numer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import pl.janicki.bramka.bledy.BladApi;

class NumerKartyTest {

    @Test
    void maskujeSzescPierwszychICzteryOstatnieCyfry() {
        assertThat(NumerKarty.z("5555555555554444").maska()).isEqualTo("555555******4444");
        assertThat(NumerKarty.z("378282246310005").maska()).isEqualTo("378282*****0005");
    }

    @Test
    void toStringNieUjawniaNumeru() {
        assertThat(NumerKarty.z("4111111111111111")).hasToString("411111******1111");
    }

    @Test
    void zwracaOsmiocyfrowyBin() {
        assertThat(NumerKarty.z("2223003122003222").bin8()).isEqualTo(22230031L);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "41111111111", "41111111111111111111", "4111-1111-1111-1111", "4111111111111112", "abcdabcdabcdabcd" })
    void odrzucaNieprawidloweNumeryKodem14(String numer) {
        assertThatThrownBy(() -> NumerKarty.z(numer))
                .isInstanceOf(BladApi.class)
                .satisfies(e -> assertThat(((BladApi) e).kod()).isEqualTo("14"));
    }
}
