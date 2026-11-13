package pl.janicki.bramka.numer;

public final class Luhn {

    private Luhn() {
    }

    public static boolean poprawny(String cyfry) {
        return cyfry.length() > 1 && sumaKontrolna(cyfry, false) % 10 == 0;
    }

    public static int cyfraKontrolna(String cyfryBezKontrolnej) {
        return (10 - sumaKontrolna(cyfryBezKontrolnej, true) % 10) % 10;
    }

    private static int sumaKontrolna(String cyfry, boolean podwajajOstatnia) {
        int suma = 0;
        boolean podwoj = podwajajOstatnia;
        for (int i = cyfry.length() - 1; i >= 0; i--) {
            int cyfra = cyfry.charAt(i) - '0';
            if (podwoj) {
                cyfra *= 2;
                if (cyfra > 9) {
                    cyfra -= 9;
                }
            }
            suma += cyfra;
            podwoj = !podwoj;
        }
        return suma;
    }
}
