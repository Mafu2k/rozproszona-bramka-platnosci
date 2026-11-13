package pl.janicki.bramka.transakcje;

import java.time.OffsetDateTime;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import pl.janicki.bramka.platnosci.WzorceDanych;
import pl.janicki.bramka.uwierzytelnianie.ZalogowanyUzytkownik;
import pl.janicki.bramka.wspolne.Strona;
import pl.janicki.bramka.wspolne.Stronicowanie;

@RestController
@RequestMapping("/api/v1/transakcje")
public class TransakcjeController {

    private final TransakcjeService transakcje;

    public TransakcjeController(TransakcjeService transakcje) {
        this.transakcje = transakcje;
    }

    @GetMapping
    public Strona<Transakcja> lista(
            @RequestParam(required = false) @Pattern(regexp = "VISA|MC|AMEX") String siec,
            @RequestParam(required = false) @Min(1) Long idKarty,
            @RequestParam(required = false) @Min(1) Long idSklepu,
            @RequestParam(required = false) @Pattern(regexp = WzorceDanych.RRN) String rrn,
            @RequestParam(required = false) @Pattern(regexp = "AUTORYZOWANA|ODRZUCONA|ZWROCONA") String status,
            @RequestParam(required = false) @Pattern(regexp = "AUTORYZACJA|ZWROT") String typ,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime od,
            @RequestParam(name = "do", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime doCzasu,
            @RequestParam(required = false) @Min(0) Long kwotaOd,
            @RequestParam(required = false) @Min(0) Long kwotaDo,
            @RequestParam(required = false, defaultValue = "dataTransakcji") @Pattern(regexp = "dataTransakcji|kwota") String sortuj,
            @RequestParam(required = false, defaultValue = "desc") @Pattern(regexp = "asc|desc") String kierunek,
            @RequestParam(required = false) Integer strona,
            @RequestParam(required = false) Integer rozmiar) {
        TransakcjeService.Filtr filtr = new TransakcjeService.Filtr(siec, idKarty, idSklepu, rrn, status, typ, od, doCzasu,
                kwotaOd, kwotaDo, sortuj, kierunek);
        return transakcje.lista(filtr, Stronicowanie.z(strona, rozmiar), ZalogowanyUzytkownik.biezacy());
    }

    @GetMapping("/{idTransakcji}")
    public TransakcjeService.TransakcjaSzczegoly szczegoly(@PathVariable @Min(1) long idTransakcji) {
        return transakcje.szczegoly(idTransakcji, ZalogowanyUzytkownik.biezacy());
    }
}
