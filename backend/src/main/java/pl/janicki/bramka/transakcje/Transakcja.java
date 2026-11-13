package pl.janicki.bramka.transakcje;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;

import pl.janicki.bramka.platnosci.KodOdpowiedzi;
import pl.janicki.bramka.wspolne.CzasUtc;
import pl.janicki.bramka.wspolne.Kwoty;

public record Transakcja(
        long id,
        String siec,
        long idKarty,
        String numerKarty,
        long idSklepu,
        String idZadania,
        String rrn,
        String typ,
        long kwota,
        String waluta,
        OffsetDateTime dataTransakcji,
        String status,
        String kod,
        String komunikat,
        Long idTransakcjiPierwotnej) {

    public static final String KOLUMNY = """
            t.id_transakcji, t.id_karty, k.numer_maskowany, t.id_sklepu, t.id_zadania, t.rrn, t.typ, t.kwota, t.waluta,
            t.data_transakcji, t.status, t.kod_odpowiedzi, t.id_transakcji_pierwotnej""";

    public static final String ZRODLO = " FROM transakcja t JOIN karta k ON k.id_karty = t.id_karty";

    public static Transakcja zWiersza(ResultSet rs, String siec) throws SQLException {
        String kod = rs.getString("kod_odpowiedzi");
        return new Transakcja(
                rs.getLong("id_transakcji"),
                siec,
                rs.getLong("id_karty"),
                rs.getString("numer_maskowany"),
                rs.getLong("id_sklepu"),
                rs.getString("id_zadania"),
                rs.getString("rrn"),
                rs.getString("typ"),
                Kwoty.naGrosze(rs.getBigDecimal("kwota")),
                rs.getString("waluta"),
                CzasUtc.odczytaj(rs, "data_transakcji"),
                rs.getString("status"),
                kod,
                KodOdpowiedzi.komunikat(kod),
                rs.getObject("id_transakcji_pierwotnej", Long.class));
    }
}
