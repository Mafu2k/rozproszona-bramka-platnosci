package pl.janicki.bramka.raporty;

import java.time.OffsetDateTime;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/v1/raporty")
@PreAuthorize("hasRole('ADMIN')")
public class RaportyController {

    private final RaportyService raporty;

    public RaportyController(RaportyService raporty) {
        this.raporty = raporty;
    }

    @GetMapping("/obroty-sklepu")
    public RaportyService.RaportObrotow obrotySklepu(
            @RequestParam @Min(1) long idSklepu,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime od,
            @RequestParam(name = "do") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime doCzasu) {
        return raporty.obrotySklepu(idSklepu, od, doCzasu);
    }

    @GetMapping("/statystyki-sieci")
    public RaportyService.StatystykiSieci statystykiSieci(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime od,
            @RequestParam(name = "do") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime doCzasu) {
        return raporty.statystykiSieci(od, doCzasu);
    }
}
