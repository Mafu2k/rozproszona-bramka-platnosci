package pl.janicki.bramka.integracja;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class Srodowisko {

    static final Path KATALOG_PROJEKTU = Path.of("..").toAbsolutePath().normalize();
    static final String SIEC_DOCKERA = "bramka-platnosci_siec-wezlow";

    private static final Map<String, String> ZMIENNE = wczytajEnv();

    private Srodowisko() {
    }

    static String zmienna(String nazwa) {
        String wartosc = System.getenv(nazwa);
        if (wartosc != null && !wartosc.isBlank()) {
            return wartosc;
        }
        wartosc = ZMIENNE.get(nazwa);
        if (wartosc == null || wartosc.isBlank()) {
            throw new IllegalStateException("Brak zmiennej " + nazwa + " w środowisku ani w .env");
        }
        return wartosc;
    }

    static String adresBramki() {
        return System.getProperty("bramka.url", "http://localhost:8080");
    }

    static String jdbcUrl(String siec) {
        int port = switch (siec) {
            case "VISA" -> 1521;
            case "MC" -> 1522;
            case "AMEX" -> 1523;
            case "DISC" -> 1524;
            default -> throw new IllegalArgumentException(siec);
        };
        return "jdbc:oracle:thin:@//localhost:" + port + "/FREEPDB1";
    }

    static List<String> polecenieDockera() {
        String polecenie = System.getProperty("docker.polecenie", System.getenv().getOrDefault("DOCKER_POLECENIE", "docker"));
        return Arrays.asList(polecenie.trim().split("\\s+"));
    }

    static String sciezkaDlaDockera(Path sciezka) {
        String bezwzgledna = sciezka.toAbsolutePath().normalize().toString();
        if (polecenieDockera().getFirst().startsWith("wsl") && bezwzgledna.matches("^[A-Za-z]:\\\\.*")) {
            return "/mnt/" + Character.toLowerCase(bezwzgledna.charAt(0)) + bezwzgledna.substring(2).replace('\\', '/');
        }
        return bezwzgledna;
    }

    private static Map<String, String> wczytajEnv() {
        Map<String, String> zmienne = new HashMap<>();
        Path plik = KATALOG_PROJEKTU.resolve(".env");
        if (!Files.exists(plik)) {
            return zmienne;
        }
        try {
            for (String linia : Files.readAllLines(plik, StandardCharsets.UTF_8)) {
                int rownosc = linia.indexOf('=');
                if (rownosc > 0 && !linia.startsWith("#")) {
                    zmienne.put(linia.substring(0, rownosc).trim(), linia.substring(rownosc + 1).trim());
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return zmienne;
    }
}
