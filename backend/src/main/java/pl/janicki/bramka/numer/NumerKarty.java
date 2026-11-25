package pl.janicki.bramka.numer;

import pl.janicki.bramka.bledy.BladApi;

public final class NumerKarty {

    public static final int MIN_DLUGOSC = 12;
    public static final int MAX_DLUGOSC = 19;

    private final String cyfry;

    private NumerKarty(String cyfry) {
        this.cyfry = cyfry;
    }

    public static NumerKarty z(String wartosc) {
        if (wartosc == null
                || wartosc.length() < MIN_DLUGOSC
                || wartosc.length() > MAX_DLUGOSC
                || !wartosc.chars().allMatch(Character::isDigit)
                || !Luhn.poprawny(wartosc)) {
            throw BladApi.nieprawidlowyNumerKarty();
        }
        return new NumerKarty(wartosc);
    }

    public String cyfry() {
        return cyfry;
    }

    public int dlugosc() {
        return cyfry.length();
    }

    public long bin8() {
        return Long.parseLong(cyfry.substring(0, 8));
    }

    public String maska() {
        return maska(cyfry);
    }

    public static String maska(String cyfry) {
        return cyfry.substring(0, 6) + "*".repeat(cyfry.length() - 10) + cyfry.substring(cyfry.length() - 4);
    }

    @Override
    public String toString() {
        return maska();
    }
}
