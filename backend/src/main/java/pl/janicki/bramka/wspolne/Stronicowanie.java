package pl.janicki.bramka.wspolne;

import java.util.Map;

import pl.janicki.bramka.bledy.BladApi;

public record Stronicowanie(int strona, int rozmiar) {

    public static final int MAKS_ROZMIAR = 100;

    public static Stronicowanie z(Integer strona, Integer rozmiar) {
        int s = strona == null ? 0 : strona;
        int r = rozmiar == null ? 20 : rozmiar;
        if (s < 0 || r < 1 || r > MAKS_ROZMIAR) {
            throw BladApi.walidacja("Nieprawidłowe stronicowanie", Map.of("rozmiar", "od 1 do " + MAKS_ROZMIAR, "strona", "od 0"));
        }
        return new Stronicowanie(s, r);
    }

    public long przesuniecie() {
        return (long) strona * rozmiar;
    }

    public long doPobraniaZKazdegoWezla() {
        return przesuniecie() + rozmiar;
    }
}
