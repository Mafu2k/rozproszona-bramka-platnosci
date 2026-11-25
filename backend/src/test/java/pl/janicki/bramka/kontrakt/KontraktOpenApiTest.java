package pl.janicki.bramka.kontrakt;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

class KontraktOpenApiTest {

    static final Path KONTRAKT = Path.of("..", "api", "openapi.yaml").toAbsolutePath().normalize();

    @Test
    void kontraktJestPoprawnymDokumentemOpenApi() {
        SwaggerParseResult wynik = new OpenAPIV3Parser().readLocation(KONTRAKT.toUri().toString(), null, null);
        assertThat(wynik.getMessages()).isEmpty();
        assertThat(wynik.getOpenAPI().getInfo().getVersion()).isEqualTo("1.0.0");
    }

    @Test
    void kontraktOpisujeWszystkieFunkcjeSystemu() {
        SwaggerParseResult wynik = new OpenAPIV3Parser().readLocation(KONTRAKT.toUri().toString(), null, null);
        Set<String> operacje = new TreeSet<>();
        wynik.getOpenAPI().getPaths().forEach((sciezka, pozycja) -> pozycja.readOperations()
                .forEach(operacja -> operacje.add(operacja.getOperationId())));
        assertThat(operacje).contains(
                "zaloguj", "wyloguj", "biezacyUzytkownik",
                "autoryzuj", "zwroc", "statusZadania",
                "listaKlientow", "dodajKlienta", "zmienKlienta", "usunKlienta", "kartyKlienta", "zablokujKlientaGlobalnie",
                "listaSklepow", "dodajSklep", "zmienSklep", "usunSklep", "wygenerujKluczApi",
                "listaKart", "wydajKarte", "zmienLimit", "zablokujKarte", "odblokujKarte",
                "listaTransakcji", "transakcja",
                "raportObrotowSklepu", "statystykiSieci",
                "stanWezlow");
    }
}
