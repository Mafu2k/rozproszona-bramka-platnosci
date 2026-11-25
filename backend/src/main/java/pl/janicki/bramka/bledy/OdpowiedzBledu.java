package pl.janicki.bramka.bledy;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

@Component
public class OdpowiedzBledu {

    private final JsonMapper json;

    public OdpowiedzBledu(JsonMapper json) {
        this.json = json;
    }

    public void wyslij(HttpServletResponse odpowiedz, BladApi blad) throws IOException {
        odpowiedz.setStatus(blad.status().value());
        odpowiedz.setContentType(MediaType.APPLICATION_JSON_VALUE);
        odpowiedz.setCharacterEncoding("UTF-8");
        if (blad.ponowZaSekund() != null) {
            odpowiedz.setHeader("Retry-After", String.valueOf(blad.ponowZaSekund()));
        }
        odpowiedz.getWriter().write(json.writeValueAsString(blad.tresc()));
    }
}
