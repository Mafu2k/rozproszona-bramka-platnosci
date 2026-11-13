package pl.janicki.bramka.bledy;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import pl.janicki.bramka.idempotencja.IdempotencjaPanelu;

@RestControllerAdvice
public class ObslugaBledow {

    private static final Logger log = LoggerFactory.getLogger(ObslugaBledow.class);

    @ExceptionHandler(BladApi.class)
    ResponseEntity<Blad> bladApi(BladApi blad) {
        ResponseEntity.BodyBuilder odpowiedz = ResponseEntity.status(blad.status());
        if (blad.ponowZaSekund() != null) {
            odpowiedz.header(HttpHeaders.RETRY_AFTER, String.valueOf(blad.ponowZaSekund()));
        }
        return odpowiedz.body(blad.tresc());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Blad> niepoprawneDane(MethodArgumentNotValidException wyjatek) {
        Map<String, Object> pola = new LinkedHashMap<>();
        for (FieldError blad : wyjatek.getBindingResult().getFieldErrors()) {
            pola.putIfAbsent(blad.getField(), blad.getDefaultMessage());
        }
        if (pola.containsKey("numerKarty")) {
            return bladApi(BladApi.nieprawidlowyNumerKarty());
        }
        if (pola.containsKey("kwota") && pola.size() == 1) {
            return bladApi(BladApi.nieprawidlowaKwota("Nieprawidłowa kwota: " + pola.get("kwota")));
        }
        return bladApi(BladApi.walidacja("Nieprawidłowe dane żądania", pola));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<Blad> niepoprawneParametry(HandlerMethodValidationException wyjatek) {
        Map<String, Object> pola = new LinkedHashMap<>();
        wyjatek.getParameterValidationResults().forEach(wynik -> pola.put(
                String.valueOf(wynik.getMethodParameter().getParameterName()),
                wynik.getResolvableErrors().isEmpty() ? "niepoprawna wartość" : wynik.getResolvableErrors().getFirst().getDefaultMessage()));
        return bladApi(BladApi.walidacja("Nieprawidłowe parametry żądania", pola));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Blad> nieczytelneDane(HttpMessageNotReadableException wyjatek) {
        return bladApi(BladApi.walidacja("Nieprawidłowy format danych JSON", null));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<Blad> brakNaglowka(MissingRequestHeaderException wyjatek) {
        if (IdempotencjaPanelu.NAGLOWEK.equalsIgnoreCase(wyjatek.getHeaderName())) {
            return bladApi(new BladApi(HttpStatus.BAD_REQUEST, "BRAK_KLUCZA_IDEMPOTENCJI",
                    "Żądanie zapisu wymaga nagłówka " + IdempotencjaPanelu.NAGLOWEK));
        }
        return bladApi(BladApi.walidacja("Brak nagłówka " + wyjatek.getHeaderName(), null));
    }

    @ExceptionHandler({ MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class })
    ResponseEntity<Blad> zlyParametr(Exception wyjatek) {
        return bladApi(BladApi.walidacja("Nieprawidłowy parametr żądania", Map.of("parametr", wyjatek.getMessage())));
    }

    @ExceptionHandler({ AccessDeniedException.class, AuthorizationDeniedException.class })
    ResponseEntity<Blad> brakUprawnien(Exception wyjatek) {
        return bladApi(BladApi.brakUprawnien("Brak uprawnień do tej operacji"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Blad> brakZasobu(NoResourceFoundException wyjatek) {
        return bladApi(BladApi.nieZnaleziono("Nie znaleziono zasobu"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Blad> zlaMetoda(HttpRequestMethodNotSupportedException wyjatek) {
        return bladApi(new BladApi(HttpStatus.METHOD_NOT_ALLOWED, "WALIDACJA", "Metoda HTTP nieobsługiwana dla tego zasobu"));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<Blad> bladBazy(DataAccessException wyjatek) {
        RuntimeException przetlumaczony = BledyBazy.przetlumacz(wyjatek, "NIEZNANA");
        if (przetlumaczony instanceof BladApi bladApi) {
            return bladApi(bladApi);
        }
        return bladSystemu(wyjatek);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Blad> bladSystemu(Exception wyjatek) {
        log.error("Nieobsłużony błąd: {}", wyjatek.getMessage(), wyjatek);
        return bladApi(new BladApi(HttpStatus.INTERNAL_SERVER_ERROR, "96", "Błąd systemu"));
    }
}
