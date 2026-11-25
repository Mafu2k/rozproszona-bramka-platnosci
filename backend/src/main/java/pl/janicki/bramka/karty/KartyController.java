package pl.janicki.bramka.karty;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import pl.janicki.bramka.idempotencja.IdempotencjaPanelu;
import pl.janicki.bramka.uwierzytelnianie.ZalogowanyUzytkownik;
import pl.janicki.bramka.wspolne.Strona;
import pl.janicki.bramka.wspolne.Stronicowanie;

@RestController
@RequestMapping("/api/v1/karty")
public class KartyController {

    private final KartyService karty;

    public KartyController(KartyService karty) {
        this.karty = karty;
    }

    @GetMapping
    public Strona<Karta> lista(
            @RequestParam(required = false) @Pattern(regexp = "VISA|MC|AMEX") String siec,
            @RequestParam(required = false) @Min(1) Long idKlienta,
            @RequestParam(required = false) @Pattern(regexp = "AKTYWNA|ZABLOKOWANA") String status,
            @RequestParam(required = false) Integer strona,
            @RequestParam(required = false) Integer rozmiar) {
        return karty.lista(siec, idKlienta, status, Stronicowanie.z(strona, rozmiar), ZalogowanyUzytkownik.biezacy());
    }

    @GetMapping("/{idKarty}")
    public Karta karta(@PathVariable @Min(1) long idKarty) {
        return karty.pobierz(idKarty, ZalogowanyUzytkownik.biezacy());
    }

    @PostMapping
    @PreAuthorize("hasRole('OPERATOR')")
    public ResponseEntity<KartyService.KartaWydana> wydaj(
            @RequestHeader(IdempotencjaPanelu.NAGLOWEK) String klucz,
            @Valid @RequestBody KartyService.KartaNowa dane) {
        return ResponseEntity.status(HttpStatus.CREATED).body(karty.wydaj(dane, klucz, ZalogowanyUzytkownik.biezacy()).tresc());
    }

    @PutMapping("/{idKarty}/limit")
    @PreAuthorize("hasRole('OPERATOR')")
    public Karta zmienLimit(@PathVariable @Min(1) long idKarty,
            @RequestHeader(IdempotencjaPanelu.NAGLOWEK) String klucz,
            @Valid @RequestBody KartyService.ZmianaLimitu zmiana) {
        return karty.zmienLimit(idKarty, zmiana, klucz, ZalogowanyUzytkownik.biezacy()).tresc();
    }

    @PostMapping("/{idKarty}/blokada")
    @PreAuthorize("hasRole('OPERATOR')")
    public Karta zablokuj(@PathVariable @Min(1) long idKarty, @RequestHeader(IdempotencjaPanelu.NAGLOWEK) String klucz) {
        return karty.zablokuj(idKarty, klucz, ZalogowanyUzytkownik.biezacy()).tresc();
    }

    @PostMapping("/{idKarty}/odblokowanie")
    @PreAuthorize("hasRole('OPERATOR')")
    public Karta odblokuj(@PathVariable @Min(1) long idKarty, @RequestHeader(IdempotencjaPanelu.NAGLOWEK) String klucz) {
        return karty.odblokuj(idKarty, klucz, ZalogowanyUzytkownik.biezacy()).tresc();
    }
}
