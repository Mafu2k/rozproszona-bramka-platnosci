package pl.janicki.bramka.uwierzytelnianie;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import pl.janicki.bramka.bledy.BladApi;

public record ZalogowanyUzytkownik(long id, String login, String rola, String siec) {

    public static final String ADMIN = "ADMIN";
    public static final String OPERATOR = "OPERATOR";

    public static ZalogowanyUzytkownik biezacy() {
        Authentication uwierzytelnienie = SecurityContextHolder.getContext().getAuthentication();
        if (uwierzytelnienie == null || !(uwierzytelnienie.getPrincipal() instanceof Jwt jwt)) {
            throw BladApi.brakUprawnien("Operacja dostępna tylko dla użytkowników panelu");
        }
        return zTokenu(jwt);
    }

    public static ZalogowanyUzytkownik zTokenu(Jwt jwt) {
        Number id = jwt.getClaim("uid");
        return new ZalogowanyUzytkownik(id.longValue(), jwt.getSubject(), jwt.getClaimAsString("rola"), jwt.getClaimAsString("siec"));
    }

    public boolean admin() {
        return ADMIN.equals(rola);
    }

    public boolean operator() {
        return OPERATOR.equals(rola);
    }

    public void sprawdzDostepDoSieci(String kodSieci) {
        if (operator() && !siec.equals(kodSieci)) {
            throw BladApi.brakUprawnien("Brak uprawnień do danych sieci " + kodSieci);
        }
    }
}
