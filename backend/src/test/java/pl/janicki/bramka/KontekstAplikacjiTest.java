package pl.janicki.bramka;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import pl.janicki.bramka.wezly.Wezly;

@SpringBootTest(properties = {
        "bramka.baza.haslo=testowe",
        "bramka.bezpieczenstwo.klucz-hmac-karty=000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f",
        "bramka.bezpieczenstwo.sekret-jwt=1f1e1d1c1b1a191817161514131211100f0e0d0c0b0a09080706050403020100",
        "bramka.wezly[0].nr=1",
        "bramka.wezly[0].kod=VISA",
        "bramka.wezly[0].url=jdbc:oracle:thin:@//localhost:59991/BRAK",
        "bramka.wezly[1].nr=2",
        "bramka.wezly[1].kod=MC",
        "bramka.wezly[1].url=jdbc:oracle:thin:@//localhost:59992/BRAK",
        "bramka.wezly[2].nr=3",
        "bramka.wezly[2].kod=AMEX",
        "bramka.wezly[2].url=jdbc:oracle:thin:@//localhost:59993/BRAK",
        "bramka.iso8583.wlaczony=false",
        "bramka.dane-demo=false",
        "bramka.uzytkownicy-startowi.haslo-admina=",
        "bramka.uzytkownicy-startowi.haslo-operatorow="
})
class KontekstAplikacjiTest {

    @Autowired
    private Wezly wezly;

    @Test
    void kontekstStartujeBezDostepnychWezlow() {
        assertThat(wezly.wszystkie()).extracting(w -> w.kodSieci()).containsExactly("VISA", "MC", "AMEX");
    }
}
