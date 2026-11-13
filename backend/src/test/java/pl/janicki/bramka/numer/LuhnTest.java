package pl.janicki.bramka.numer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LuhnTest {

    @ParameterizedTest
    @ValueSource(strings = { "4111111111111111", "5555555555554444", "2223003122003222", "378282246310005", "4012888888881881" })
    void akceptujePoprawneNumery(String numer) {
        assertThat(Luhn.poprawny(numer)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "4111111111111112", "5555555555554445", "378282246310006", "1234567890123456" })
    void odrzucaNumeryZBlednaCyfraKontrolna(String numer) {
        assertThat(Luhn.poprawny(numer)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "4111111111111111", "5555555555554444", "378282246310005" })
    void wyliczaCyfreKontrolna(String numer) {
        String bezKontrolnej = numer.substring(0, numer.length() - 1);
        assertThat(Luhn.cyfraKontrolna(bezKontrolnej)).isEqualTo(numer.charAt(numer.length() - 1) - '0');
    }
}
