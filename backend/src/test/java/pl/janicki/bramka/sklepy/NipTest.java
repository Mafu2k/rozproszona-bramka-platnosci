package pl.janicki.bramka.sklepy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NipTest {

    @ParameterizedTest
    @ValueSource(strings = { "5260181596", "1860913901", "6281948218", "9935181904", "9378657972", "5432319484" })
    void akceptujePoprawneNip(String nip) {
        assertThat(Nip.Walidator.poprawny(nip)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "5260181597", "123456789", "12345678901", "52601815AB" })
    void odrzucaNiepoprawneNip(String nip) {
        assertThat(Nip.Walidator.poprawny(nip)).isFalse();
    }
}
