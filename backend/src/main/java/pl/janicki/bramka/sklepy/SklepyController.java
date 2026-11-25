package pl.janicki.bramka.sklepy;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import jakarta.validation.constraints.Size;
import pl.janicki.bramka.idempotencja.IdempotencjaPanelu;
import pl.janicki.bramka.uwierzytelnianie.ZalogowanyUzytkownik;
import pl.janicki.bramka.wspolne.Strona;
import pl.janicki.bramka.wspolne.Stronicowanie;

@RestController
@RequestMapping("/api/v1/sklepy")
public class SklepyController {

    private final SklepyService sklepy;

    public SklepyController(SklepyService sklepy) {
        this.sklepy = sklepy;
    }

    @GetMapping
    public Strona<Sklep> lista(
            @RequestParam(required = false) @Size(max = 100) String szukaj,
            @RequestParam(required = false) @Pattern(regexp = "AKTYWNY|ZAWIESZONY|USUNIETY") String status,
            @RequestParam(required = false) Integer strona,
            @RequestParam(required = false) Integer rozmiar) {
        return sklepy.lista(szukaj, status, Stronicowanie.z(strona, rozmiar));
    }

    @GetMapping("/{idSklepu}")
    public Sklep sklep(@PathVariable @Min(1) long idSklepu) {
        return sklepy.pobierz(idSklepu);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Sklep> dodaj(@RequestHeader(IdempotencjaPanelu.NAGLOWEK) String klucz,
            @Valid @RequestBody SklepyService.SklepDane dane) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sklepy.dodaj(dane, klucz, ZalogowanyUzytkownik.biezacy()).tresc());
    }

    @PutMapping("/{idSklepu}")
    @PreAuthorize("hasRole('ADMIN')")
    public Sklep zmien(@PathVariable @Min(1) long idSklepu, @RequestHeader(IdempotencjaPanelu.NAGLOWEK) String klucz,
            @Valid @RequestBody SklepyService.SklepDane dane) {
        return sklepy.zmien(idSklepu, dane, klucz, ZalogowanyUzytkownik.biezacy()).tresc();
    }

    @DeleteMapping("/{idSklepu}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> usun(@PathVariable @Min(1) long idSklepu, @RequestHeader(IdempotencjaPanelu.NAGLOWEK) String klucz) {
        sklepy.usun(idSklepu, klucz, ZalogowanyUzytkownik.biezacy());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{idSklepu}/klucz-api")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SklepyService.NowyKluczApi> nowyKluczApi(@PathVariable @Min(1) long idSklepu,
            @RequestHeader(IdempotencjaPanelu.NAGLOWEK) String klucz) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sklepy.nowyKluczApi(idSklepu, klucz, ZalogowanyUzytkownik.biezacy()).tresc());
    }
}
