package pl.janicki.bramka.uwierzytelnianie;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/auth")
public class LogowanieController {

    public record Logowanie(@NotBlank @Size(max = 50) String login, @NotBlank @Size(max = 200) String haslo) {

        @Override
        public String toString() {
            return "Logowanie[login=" + login + "]";
        }
    }

    private final LogowanieService logowanie;
    private final UniewaznioneTokeny uniewaznione;

    public LogowanieController(LogowanieService logowanie, UniewaznioneTokeny uniewaznione) {
        this.logowanie = logowanie;
        this.uniewaznione = uniewaznione;
    }

    @PostMapping("/logowanie")
    public LogowanieService.TokenLogowania zaloguj(@Valid @RequestBody Logowanie dane) {
        return logowanie.zaloguj(dane.login(), dane.haslo());
    }

    @PostMapping("/wylogowanie")
    public ResponseEntity<Void> wyloguj(@AuthenticationPrincipal Jwt jwt) {
        uniewaznione.uniewaznij(jwt.getId(), jwt.getExpiresAt());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/ja")
    public LogowanieService.UzytkownikPanelu ja(@AuthenticationPrincipal Jwt jwt) {
        ZalogowanyUzytkownik uzytkownik = ZalogowanyUzytkownik.zTokenu(jwt);
        return new LogowanieService.UzytkownikPanelu(uzytkownik.id(), uzytkownik.login(), uzytkownik.rola(), uzytkownik.siec());
    }
}
