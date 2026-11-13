package pl.janicki.bramka.wspolne;

import java.util.List;

public record Strona<T>(
        List<T> elementy,
        int strona,
        int rozmiar,
        long lacznie,
        String zrodlo,
        boolean czesciowy,
        List<String> brakujaceSieci) {

    public static <T> Strona<T> zJednegoZrodla(List<T> elementy, int strona, int rozmiar, long lacznie, String zrodlo) {
        return new Strona<>(elementy, strona, rozmiar, lacznie, zrodlo, false, List.of());
    }
}
