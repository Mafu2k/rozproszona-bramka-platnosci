package pl.janicki.bramka.konfiguracja;

import java.util.HexFormat;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import pl.janicki.bramka.bledy.BladApi;
import pl.janicki.bramka.bledy.OdpowiedzBledu;
import pl.janicki.bramka.uwierzytelnianie.LogowanieService;
import pl.janicki.bramka.uwierzytelnianie.RejestrSklepow;
import pl.janicki.bramka.uwierzytelnianie.UniewaznioneTokeny;
import pl.janicki.bramka.uwierzytelnianie.UwierzytelnianieSklepuFilter;

@Configuration
@EnableMethodSecurity
public class BezpieczenstwoKonfiguracja {

    private static final int MIN_DLUGOSC_SEKRETU = 32;

    @Bean
    @Order(1)
    SecurityFilterChain lancuchSklepow(HttpSecurity http, RejestrSklepow rejestrSklepow, OdpowiedzBledu odpowiedzBledu) throws Exception {
        http.securityMatcher("/api/v1/platnosci/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sesja -> sesja.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(formularz -> formularz.disable())
                .addFilterBefore(new UwierzytelnianieSklepuFilter(rejestrSklepow, odpowiedzBledu), AuthorizationFilter.class)
                .authorizeHttpRequests(dostep -> dostep.anyRequest().hasRole("SKLEP"))
                .exceptionHandling(obsluga -> obsluga
                        .authenticationEntryPoint(brakUwierzytelnienia(odpowiedzBledu, "Wymagany klucz API w nagłówku X-Api-Key"))
                        .accessDeniedHandler(brakUprawnien(odpowiedzBledu)));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain lancuchPanelu(HttpSecurity http, JwtDecoder dekoder, OdpowiedzBledu odpowiedzBledu) throws Exception {
        AuthenticationEntryPoint brakTokenu = brakUwierzytelnienia(odpowiedzBledu, "Wymagany token w nagłówku Authorization");
        http.securityMatcher("/api/**", "/actuator/**")
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(sesja -> sesja.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(formularz -> formularz.disable())
                .authorizeHttpRequests(dostep -> dostep
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logowanie").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(serwer -> serwer
                        .jwt(jwt -> jwt.decoder(dekoder).jwtAuthenticationConverter(konwerterRol()))
                        .authenticationEntryPoint(brakTokenu))
                .exceptionHandling(obsluga -> obsluga
                        .authenticationEntryPoint(brakTokenu)
                        .accessDeniedHandler(brakUprawnien(odpowiedzBledu)));
        return http.build();
    }

    @Bean
    PasswordEncoder kodowanieHasel() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    JwtEncoder koderTokenow(BramkaProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(kluczJwt(properties)));
    }

    @Bean
    JwtDecoder dekoderTokenow(BramkaProperties properties, UniewaznioneTokeny uniewaznione) {
        NimbusJwtDecoder dekoder = NimbusJwtDecoder.withSecretKey(kluczJwt(properties)).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> nieWylogowany = jwt -> uniewaznione.uniewazniony(jwt.getId())
                ? OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Token unieważniony", null))
                : OAuth2TokenValidatorResult.success();
        dekoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(LogowanieService.WYSTAWCA), nieWylogowany));
        return dekoder;
    }

    @Bean
    CorsConfigurationSource zrodlaCors(BramkaProperties properties) {
        CorsConfiguration konfiguracja = new CorsConfiguration();
        konfiguracja.setAllowedOrigins(properties.bezpieczenstwo().dozwoloneZrodlaCors());
        konfiguracja.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        konfiguracja.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        konfiguracja.setExposedHeaders(List.of("Retry-After"));
        konfiguracja.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource zrodlo = new UrlBasedCorsConfigurationSource();
        zrodlo.registerCorsConfiguration("/api/**", konfiguracja);
        return zrodlo;
    }

    private static JwtAuthenticationConverter konwerterRol() {
        JwtGrantedAuthoritiesConverter role = new JwtGrantedAuthoritiesConverter();
        role.setAuthoritiesClaimName("rola");
        role.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter konwerter = new JwtAuthenticationConverter();
        konwerter.setJwtGrantedAuthoritiesConverter(role);
        return konwerter;
    }

    private static SecretKey kluczJwt(BramkaProperties properties) {
        byte[] bajty = HexFormat.of().parseHex(properties.bezpieczenstwo().sekretJwt());
        if (bajty.length < MIN_DLUGOSC_SEKRETU) {
            throw new IllegalStateException("Sekret JWT musi mieć co najmniej 32 bajty (64 znaki hex)");
        }
        return new SecretKeySpec(bajty, "HmacSHA256");
    }

    private static AuthenticationEntryPoint brakUwierzytelnienia(OdpowiedzBledu odpowiedzBledu, String komunikat) {
        return (zadanie, odpowiedz, wyjatek) -> {
            odpowiedz.setHeader("WWW-Authenticate", "Bearer");
            odpowiedzBledu.wyslij(odpowiedz, new BladApi(HttpStatus.UNAUTHORIZED, "BRAK_UWIERZYTELNIENIA", komunikat));
        };
    }

    private static AccessDeniedHandler brakUprawnien(OdpowiedzBledu odpowiedzBledu) {
        return (zadanie, odpowiedz, wyjatek) -> odpowiedzBledu.wyslij(odpowiedz, BladApi.brakUprawnien("Brak uprawnień do tej operacji"));
    }
}
