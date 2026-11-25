package pl.janicki.bramka.wspolne;

import java.math.BigDecimal;

public final class Kwoty {

    private Kwoty() {
    }

    public static BigDecimal zGroszy(long grosze) {
        return BigDecimal.valueOf(grosze, 2);
    }

    public static long naGrosze(BigDecimal kwota) {
        return kwota == null ? 0 : kwota.movePointRight(2).longValueExact();
    }
}
