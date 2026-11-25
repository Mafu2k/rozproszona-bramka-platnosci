package pl.janicki.bramka.bledy;

import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientConnectionException;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;

public final class BledyBazy {

    private static final Set<Integer> BLEDY_POLACZENIA = Set.of(
            17002, 17008, 17410, 12514, 12541, 12543, 12170, 12545, 12537, 3113, 3114, 3135, 1033, 1034, 1089, 1109);

    private static final Set<Integer> BLEDY_LACZA = Set.of(
            2068, 2019, 12154, 12262, 12505, 12514, 12528, 12541, 12543, 12170, 12545, 12537, 3113, 3114, 3135, 3150,
            1033, 1034, 1089);

    private static final Pattern LACZE = Pattern.compile("(?:preceding line from|following severe error from) ([A-Za-z0-9_]+)");
    private static final Pattern KOD_ORA = Pattern.compile("ORA-(\\d{5})");
    private static final Pattern NIEDOSTEPNA_SIEC = Pattern.compile("ORA-20091: [^:\\n]*: ([A-Za-z0-9_]+)");

    private BledyBazy() {
    }

    public static RuntimeException przetlumacz(Throwable blad, String kodSieciWezla) {
        if (blad instanceof BladApi bladApi) {
            return bladApi;
        }
        Optional<String> zdalnaSiec = niedostepnaZdalnaSiec(blad).or(() -> zgloszonaNiedostepnosc(blad));
        if (zdalnaSiec.isPresent()) {
            return BladApi.siecNiedostepna(zdalnaSiec.get());
        }
        if (wezelNiedostepny(blad)) {
            return BladApi.siecNiedostepna(kodSieciWezla);
        }
        Optional<RuntimeException> aplikacyjny = kodAplikacyjny(blad).map(BledyBazy::bladAplikacyjny);
        if (aplikacyjny.isPresent()) {
            return aplikacyjny.get();
        }
        if (blad instanceof DuplicateKeyException || kodyOracle(blad).contains(1)) {
            return BladApi.konflikt("DUPLIKAT", "Rekord o takich danych już istnieje");
        }
        return blad instanceof RuntimeException r ? r : new IllegalStateException(blad);
    }

    public static boolean wezelNiedostepny(Throwable blad) {
        if (niedostepnaZdalnaSiec(blad).isPresent()) {
            return false;
        }
        for (Throwable t = blad; t != null; t = t.getCause()) {
            if (t instanceof SQLTransientConnectionException || t instanceof SQLRecoverableException) {
                return true;
            }
            if (t instanceof SQLException sql && BLEDY_POLACZENIA.contains(sql.getErrorCode())) {
                return true;
            }
            if (t instanceof java.net.ConnectException || t instanceof java.net.SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    public static Optional<String> niedostepnaZdalnaSiec(Throwable blad) {
        for (Throwable t = blad; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql) {
                String komunikat = String.valueOf(sql.getMessage());
                Set<Integer> kody = kodyZKomunikatu(komunikat);
                kody.add(sql.getErrorCode());
                Matcher lacze = LACZE.matcher(komunikat);
                if (kody.stream().anyMatch(BLEDY_LACZA::contains) && lacze.find()) {
                    return Optional.of(lacze.group(1).toUpperCase());
                }
            }
        }
        return Optional.empty();
    }

    public static Optional<String> zgloszonaNiedostepnosc(Throwable blad) {
        for (Throwable t = blad; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getMessage() != null) {
                Matcher dopasowanie = NIEDOSTEPNA_SIEC.matcher(sql.getMessage());
                if (dopasowanie.find()) {
                    return Optional.of(dopasowanie.group(1).toUpperCase());
                }
            }
        }
        return Optional.empty();
    }

    public static Set<Integer> kodyOracle(Throwable blad) {
        Set<Integer> kody = new LinkedHashSet<>();
        for (Throwable t = blad; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql) {
                if (sql.getErrorCode() != 0) {
                    kody.add(sql.getErrorCode());
                }
                kody.addAll(kodyZKomunikatu(sql.getMessage()));
            }
        }
        return kody;
    }

    private static Optional<Integer> kodAplikacyjny(Throwable blad) {
        return kodyOracle(blad).stream().filter(kod -> kod >= 20000 && kod <= 20999).findFirst();
    }

    private static Set<Integer> kodyZKomunikatu(String komunikat) {
        Set<Integer> kody = new LinkedHashSet<>();
        if (komunikat != null) {
            Matcher dopasowanie = KOD_ORA.matcher(komunikat);
            while (dopasowanie.find()) {
                kody.add(Integer.parseInt(dopasowanie.group(1)));
            }
        }
        return kody;
    }

    private static RuntimeException bladAplikacyjny(int kod) {
        return switch (kod) {
            case 20020 -> BladApi.replikacjaWToku("Dane są w trakcie replikacji na węzeł sieci, ponów za chwilę");
            case 20094 -> new BladApi(HttpStatus.CONFLICT, "94", "Identyfikator żądania użyty z innymi danymi");
            case 20043 -> BladApi.konflikt("KLIENT_MA_AKTYWNE_KARTY", "Klient ma aktywne karty");
            case 20044 -> BladApi.nieZnaleziono("Klient nie istnieje");
            case 20045, 20048 -> BladApi.konflikt("KLIENT_NIEAKTYWNY", "Klient jest zablokowany albo usunięty");
            case 20046 -> BladApi.konflikt("LIMIT_PONIZEJ_WYKORZYSTANIA", "Nowy limit jest niższy niż wykorzystane środki");
            case 20047 -> BladApi.nieZnaleziono("Karta nie istnieje");
            case 20050 -> BladApi.konflikt("DUPLIKAT", "Zakresy BIN nie mogą się nakładać");
            default -> new BladApi(HttpStatus.INTERNAL_SERVER_ERROR, "96", "Błąd systemu");
        };
    }
}
