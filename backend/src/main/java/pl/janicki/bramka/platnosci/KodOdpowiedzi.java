package pl.janicki.bramka.platnosci;

import java.util.Map;

public final class KodOdpowiedzi {

    public static final String ZATWIERDZONA = "00";

    private static final Map<String, String> KOMUNIKATY = Map.ofEntries(
            Map.entry("00", "Transakcja zatwierdzona"),
            Map.entry("03", "Nieprawidłowy akceptant"),
            Map.entry("12", "Nieprawidłowa transakcja"),
            Map.entry("13", "Nieprawidłowa kwota"),
            Map.entry("14", "Nieznany numer karty"),
            Map.entry("25", "Nie znaleziono transakcji pierwotnej"),
            Map.entry("51", "Brak wystarczających środków"),
            Map.entry("54", "Karta przeterminowana"),
            Map.entry("57", "Transakcja niedozwolona dla karty"),
            Map.entry("62", "Karta zablokowana"),
            Map.entry("91", "Sieć niedostępna"),
            Map.entry("94", "Duplikat transakcji"),
            Map.entry("96", "Błąd systemu"));

    private KodOdpowiedzi() {
    }

    public static String komunikat(String kod) {
        return KOMUNIKATY.getOrDefault(kod, "Nieznany kod odpowiedzi");
    }

    public static boolean zatwierdzona(String kod) {
        return ZATWIERDZONA.equals(kod);
    }
}
