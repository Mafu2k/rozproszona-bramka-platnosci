package pl.janicki.bramka.wezly;

import java.util.List;
import java.util.Map;

public record WynikZWezlow<T>(Map<String, T> wyniki, List<String> brakujaceSieci) {

    public boolean czesciowy() {
        return !brakujaceSieci.isEmpty();
    }
}
