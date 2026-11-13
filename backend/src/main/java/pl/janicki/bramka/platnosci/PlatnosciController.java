package pl.janicki.bramka.platnosci;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pl.janicki.bramka.uwierzytelnianie.SklepUwierzytelniony;

@RestController
@RequestMapping("/api/v1/platnosci")
public class PlatnosciController {

    private final PlatnosciService platnosci;

    public PlatnosciController(PlatnosciService platnosci) {
        this.platnosci = platnosci;
    }

    @PostMapping("/autoryzacje")
    public WynikPlatnosci autoryzuj(@AuthenticationPrincipal SklepUwierzytelniony sklep,
            @Valid @RequestBody ZadanieAutoryzacji zadanie) {
        return platnosci.autoryzuj(sklep.id(), zadanie);
    }

    @PostMapping("/zwroty")
    public WynikPlatnosci zwroc(@AuthenticationPrincipal SklepUwierzytelniony sklep,
            @Valid @RequestBody ZadanieZwrotu zadanie) {
        return platnosci.zwroc(sklep.id(), zadanie);
    }

    @GetMapping("/{idZadania}")
    public WynikPlatnosci status(@AuthenticationPrincipal SklepUwierzytelniony sklep,
            @PathVariable @Size(max = 64) @Pattern(regexp = WzorceDanych.ID_ZADANIA) String idZadania) {
        return platnosci.status(sklep.id(), idZadania);
    }
}
