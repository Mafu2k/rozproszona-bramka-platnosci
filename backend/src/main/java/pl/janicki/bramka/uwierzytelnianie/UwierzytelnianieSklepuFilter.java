package pl.janicki.bramka.uwierzytelnianie;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pl.janicki.bramka.bledy.BladApi;
import pl.janicki.bramka.bledy.OdpowiedzBledu;

public class UwierzytelnianieSklepuFilter extends OncePerRequestFilter {

    public static final String NAGLOWEK = "X-Api-Key";

    private final RejestrSklepow rejestrSklepow;
    private final OdpowiedzBledu odpowiedzBledu;

    public UwierzytelnianieSklepuFilter(RejestrSklepow rejestrSklepow, OdpowiedzBledu odpowiedzBledu) {
        this.rejestrSklepow = rejestrSklepow;
        this.odpowiedzBledu = odpowiedzBledu;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest zadanie, HttpServletResponse odpowiedz, FilterChain lancuch)
            throws ServletException, IOException {
        String klucz = zadanie.getHeader(NAGLOWEK);
        if (klucz == null || klucz.isBlank()) {
            lancuch.doFilter(zadanie, odpowiedz);
            return;
        }
        Optional<SklepUwierzytelniony> sklep;
        try {
            sklep = rejestrSklepow.poKluczu(klucz.trim());
        } catch (BladApi blad) {
            odpowiedzBledu.wyslij(odpowiedz, blad);
            return;
        }
        if (sklep.isEmpty()) {
            odpowiedzBledu.wyslij(odpowiedz, new BladApi(HttpStatus.UNAUTHORIZED, "BRAK_UWIERZYTELNIENIA", "Nieprawidłowy klucz API"));
            return;
        }
        if (!sklep.get().aktywny()) {
            odpowiedzBledu.wyslij(odpowiedz, BladApi.brakUprawnien("Sklep nie jest aktywny"));
            return;
        }
        UsernamePasswordAuthenticationToken uwierzytelnienie = UsernamePasswordAuthenticationToken.authenticated(
                sklep.get(), null, List.of(new SimpleGrantedAuthority("ROLE_SKLEP")));
        SecurityContextHolder.getContext().setAuthentication(uwierzytelnienie);
        lancuch.doFilter(zadanie, odpowiedz);
    }
}
