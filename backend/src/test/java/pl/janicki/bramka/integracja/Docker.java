package pl.janicki.bramka.integracja;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class Docker {

    private Docker() {
    }

    static String wykonaj(String... argumenty) {
        return wykonajZWejsciem(null, argumenty);
    }

    static String wykonajZWejsciem(String wejscie, String... argumenty) {
        List<String> polecenie = new ArrayList<>(Srodowisko.polecenieDockera());
        polecenie.addAll(List.of(argumenty));
        try {
            Process proces = new ProcessBuilder(polecenie).redirectErrorStream(true).start();
            if (wejscie != null) {
                try (OutputStream strumien = proces.getOutputStream()) {
                    strumien.write(wejscie.getBytes(StandardCharsets.UTF_8));
                }
            }
            String wyjscie = new String(proces.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!proces.waitFor(10, TimeUnit.MINUTES)) {
                proces.destroyForcibly();
                throw new IllegalStateException("Polecenie docker nie zakończyło się w czasie: " + argumenty[0]);
            }
            if (proces.exitValue() != 0) {
                throw new IllegalStateException("docker " + argumenty[0] + " zakończony kodem " + proces.exitValue() + ": " + wyjscie);
            }
            return wyjscie;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    static String sqlJakoSys(String kontener, String polecenia) {
        return wykonajZWejsciem("ALTER SESSION SET CONTAINER = FREEPDB1;\n" + polecenia + "\nEXIT\n",
                "exec", "-i", kontener, "sqlplus", "-s", "/", "as", "sysdba");
    }

    static void zatrzymaj(String kontener) {
        wykonaj("stop", kontener);
    }

    static void uruchom(String kontener, String siec) {
        wykonaj("start", kontener);
        czekajNaBaze(siec);
    }

    static void odlaczOdSieci(String kontener) {
        wykonaj("network", "disconnect", Srodowisko.SIEC_DOCKERA, kontener);
    }

    static void podlaczDoSieci(String kontener) {
        wykonaj("network", "connect", Srodowisko.SIEC_DOCKERA, kontener);
    }

    static void czekajNaBaze(String siec) {
        Czekanie.az(Duration.ofMinutes(5), "baza " + siec + " przyjmuje połączenia", () -> Bazy.dostepna(siec));
    }
}
