package pl.janicki.bramka.klienci;

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
@RequestMapping("/api/v1/klienci")
public class KlienciController {

    private final KlienciService klienci;

    public KlienciController(KlienciService klienci) {
        this.klienci = klienci;
    }

    @GetMapping
    public Strona<Klient> lista(
            @RequestParam(required = false) @Size(max = 100) String szukaj,
            @RequestParam(required = false) @Pattern(regexp = "AKTYWNY|ZABLOKOWANY|USUNIETY") String status,
            @RequestParam(required = false) Integer strona,
            @RequestParam(required = false) Integer rozmiar) {
        return klienci.lista(szukaj, status, Stronicowanie.z(strona, rozmiar));
    }

    @GetMapping("/{idKlienta}")
    public Klient klient(@PathVariable @Min(1) long idKlienta) {
        return klienci.pobierz(idKlienta);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Klient> dodaj(@RequestHeader(IdempotencjaPanelu.NAGLOWEK) String klucz,
            @Valid @RequestBody KlienciService.KlientDane dane) {
        return ResponseEntity.status(HttpStatus.CREATED).body(klienci.dodaj(dane, klucz, ZalogowanyUzytkownik.biezacy()).tresc());
    }

    @PutMapping("/{idKlienta}")
    @PreAuthorize("hasRole('ADMIN')")
    public Klient zmien(@PathVariable @Min(1) long idKlienta, @RequestHeader(IdempotencjaPanelu.NAGLOWEK) String klucz,
            @Valid @RequestBody KlienciService.KlientDane dane) {
        return klienci.zmien(idKlienta, dane, klucz, ZalogowanyUzytkownik.biezacy()).tresc();
    }

    @DeleteMapping("/{idKlienta}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> usun(@PathVariable @Min(1) long idKlienta, @RequestHeader(IdempotencjaPanelu.NAGLOWEK) String klucz) {
        klienci.usun(idKlienta, klucz, ZalogowanyUzytkownik.biezacy());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{idKlienta}/karty")
    public KlienciService.ListaKart karty(@PathVariable @Min(1) long idKlienta) {
        return klienci.karty(idKlienta, ZalogowanyUzytkownik.biezacy());
    }

    @PostMapping("/{idKlienta}/blokada-globalna")
    @PreAuthorize("hasRole('ADMIN')")
    public KlienciService.WynikBlokadyGlobalnej zablokujGlobalnie(@PathVariable @Min(1) long idKlienta,
            @RequestHeader(IdempotencjaPanelu.NAGLOWEK) String klucz) {
        return klienci.zablokujGlobalnie(idKlienta, klucz, ZalogowanyUzytkownik.biezacy()).tresc();
    }
}
