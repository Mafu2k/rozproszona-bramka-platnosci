package pl.janicki.bramka.uwierzytelnianie;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import pl.janicki.bramka.bledy.BladApi;
import pl.janicki.bramka.konfiguracja.BramkaProperties;
import pl.janicki.bramka.wezly.Wezly;

@Service
public class LogowanieService {

    public static final String WYSTAWCA = "bramka-platnosci";

    private record DaneUzytkownika(long id, String login, String hashHasla, String rola, Integer idSieci, String status) {
    }

    public record UzytkownikPanelu(long id, String login, String rola, String siec) {
    }

    public record TokenLogowania(String token, String typ, OffsetDateTime wygasa, UzytkownikPanelu uzytkownik) {
    }

    private final Wezly wezly;
    private final PasswordEncoder hasla;
    private final JwtEncoder tokeny;
    private final LimitProbLogowania limit;
    private final BramkaProperties properties;
    private final String atrapaHasha;

    public LogowanieService(Wezly wezly, PasswordEncoder hasla, JwtEncoder tokeny, LimitProbLogowania limit, BramkaProperties properties) {
        this.wezly = wezly;
        this.hasla = hasla;
        this.tokeny = tokeny;
        this.limit = limit;
        this.properties = properties;
        this.atrapaHasha = hasla.encode(UUID.randomUUID().toString());
    }

    public TokenLogowania zaloguj(String login, String haslo) {
        limit.sprawdz(login);
        Optional<DaneUzytkownika> uzytkownik = wezly.czytajDaneWspolne(wezel -> wezel.jdbc()
                .sql("SELECT id_uzytkownika, login, hash_hasla, rola, id_sieci, status FROM uzytkownik WHERE login = ?")
                .param(login.trim())
                .query((rs, i) -> new DaneUzytkownika(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        (Integer) rs.getObject(5, Integer.class), rs.getString(6)))
                .optional()).wartosc();
        boolean hasloPoprawne = hasla.matches(haslo, uzytkownik.map(DaneUzytkownika::hashHasla).orElse(atrapaHasha));
        if (uzytkownik.isEmpty() || !hasloPoprawne || !"AKTYWNY".equals(uzytkownik.get().status())) {
            limit.porazka(login);
            throw new BladApi(HttpStatus.UNAUTHORIZED, "NIEPRAWIDLOWE_DANE_LOGOWANIA", "Nieprawidłowy login lub hasło");
        }
        limit.sukces(login);
        DaneUzytkownika dane = uzytkownik.get();
        UzytkownikPanelu panel = new UzytkownikPanelu(dane.id(), dane.login(), dane.rola(),
                dane.idSieci() == null ? null : wezly.wezel(dane.idSieci()).kodSieci());
        Instant teraz = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant wygasa = teraz.plus(properties.bezpieczenstwo().waznoscTokenu());
        JwtClaimsSet.Builder roszczenia = JwtClaimsSet.builder()
                .issuer(WYSTAWCA)
                .subject(panel.login())
                .id(UUID.randomUUID().toString())
                .issuedAt(teraz)
                .expiresAt(wygasa)
                .claim("uid", panel.id())
                .claim("rola", panel.rola());
        if (panel.siec() != null) {
            roszczenia.claim("siec", panel.siec());
        }
        String token = tokeny.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), roszczenia.build()))
                .getTokenValue();
        return new TokenLogowania(token, "Bearer", wygasa.atOffset(ZoneOffset.UTC), panel);
    }
}
