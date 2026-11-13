package pl.janicki.bramka.platnosci;

public final class WzorceDanych {

    public static final String ID_ZADANIA = "^[A-Za-z0-9._:-]+$";
    public static final String WALUTA = "^[A-Z]{3}$";
    public static final String RRN = "^[0-9]{12}$";
    public static final long MAKS_KWOTA = 9_999_999_999L;

    private WzorceDanych() {
    }
}
