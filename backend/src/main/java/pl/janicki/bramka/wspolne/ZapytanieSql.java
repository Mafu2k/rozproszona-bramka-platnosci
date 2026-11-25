package pl.janicki.bramka.wspolne;

import java.util.ArrayList;
import java.util.List;

public class ZapytanieSql {

    private final List<String> warunki = new ArrayList<>();
    private final List<Object> parametry = new ArrayList<>();

    public ZapytanieSql warunek(boolean dodaj, String warunek, Object... wartosci) {
        if (dodaj) {
            warunki.add(warunek);
            parametry.addAll(List.of(wartosci));
        }
        return this;
    }

    public String where() {
        return warunki.isEmpty() ? "" : " WHERE " + String.join(" AND ", warunki);
    }

    public String and() {
        return warunki.isEmpty() ? "" : " AND " + String.join(" AND ", warunki);
    }

    public List<Object> parametry() {
        return List.copyOf(parametry);
    }

    public static String wzorzecLike(String fragment) {
        String bezpieczny = fragment.trim().toUpperCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + bezpieczny + "%";
    }
}
