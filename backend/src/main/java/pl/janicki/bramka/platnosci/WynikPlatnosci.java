package pl.janicki.bramka.platnosci;

import java.time.OffsetDateTime;

public record WynikPlatnosci(
        String idZadania,
        Decyzja decyzja,
        String kod,
        String komunikat,
        String rrn,
        String typ,
        long kwota,
        String waluta,
        String siec,
        String numerKarty,
        String statusTransakcji,
        OffsetDateTime dataTransakcji,
        String rrnPierwotnej,
        boolean powtorzona) {

    public enum Decyzja {
        ZATWIERDZONA, ODRZUCONA
    }

    public static Decyzja decyzja(String kod) {
        return KodOdpowiedzi.zatwierdzona(kod) ? Decyzja.ZATWIERDZONA : Decyzja.ODRZUCONA;
    }
}
