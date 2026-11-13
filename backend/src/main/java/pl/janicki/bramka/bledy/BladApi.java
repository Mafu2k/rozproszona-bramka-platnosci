package pl.janicki.bramka.bledy;

import java.util.Map;

import org.springframework.http.HttpStatus;

public class BladApi extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final HttpStatus status;
    private final String kod;
    private final transient Map<String, Object> szczegoly;
    private final Integer ponowZaSekund;

    public BladApi(HttpStatus status, String kod, String komunikat, Map<String, Object> szczegoly, Integer ponowZaSekund) {
        super(komunikat);
        this.status = status;
        this.kod = kod;
        this.szczegoly = szczegoly;
        this.ponowZaSekund = ponowZaSekund;
    }

    public BladApi(HttpStatus status, String kod, String komunikat) {
        this(status, kod, komunikat, null, null);
    }

    public static BladApi walidacja(String komunikat, Map<String, Object> szczegoly) {
        return new BladApi(HttpStatus.BAD_REQUEST, "WALIDACJA", komunikat, szczegoly, null);
    }

    public static BladApi nieprawidlowyNumerKarty() {
        return new BladApi(HttpStatus.BAD_REQUEST, "14", "Nieprawidłowy numer karty");
    }

    public static BladApi nieprawidlowaKwota(String komunikat) {
        return new BladApi(HttpStatus.BAD_REQUEST, "13", komunikat);
    }

    public static BladApi nieZnaleziono(String komunikat) {
        return new BladApi(HttpStatus.NOT_FOUND, "NIE_ZNALEZIONO", komunikat);
    }

    public static BladApi brakUprawnien(String komunikat) {
        return new BladApi(HttpStatus.FORBIDDEN, "BRAK_UPRAWNIEN", komunikat);
    }

    public static BladApi konflikt(String kod, String komunikat) {
        return new BladApi(HttpStatus.CONFLICT, kod, komunikat);
    }

    public static BladApi duplikatZadania(String identyfikator) {
        return new BladApi(HttpStatus.CONFLICT, "94", "Identyfikator żądania użyty z innymi danymi",
                Map.of("idZadania", identyfikator), null);
    }

    public static BladApi replikacjaWToku(String komunikat) {
        return new BladApi(HttpStatus.CONFLICT, "REPLIKACJA_W_TOKU", komunikat, null, 30);
    }

    public static BladApi siecNiedostepna(String kodSieci) {
        return new BladApi(HttpStatus.SERVICE_UNAVAILABLE, "91", "Sieć niedostępna", Map.of("siec", kodSieci), null);
    }

    public HttpStatus status() {
        return status;
    }

    public String kod() {
        return kod;
    }

    public Map<String, Object> szczegoly() {
        return szczegoly;
    }

    public Integer ponowZaSekund() {
        return ponowZaSekund;
    }

    public Blad tresc() {
        return new Blad(kod, getMessage(), szczegoly);
    }
}
