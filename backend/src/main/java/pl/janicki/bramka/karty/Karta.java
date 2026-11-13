package pl.janicki.bramka.karty;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;

import pl.janicki.bramka.wspolne.CzasUtc;
import pl.janicki.bramka.wspolne.Kwoty;

public record Karta(
        long id,
        long idKlienta,
        String siec,
        String numerKarty,
        String dataWaznosci,
        long limitKredytowy,
        long dostepneSrodki,
        String waluta,
        String status,
        OffsetDateTime dataWydania) {

    public static final String KOLUMNY = """
            id_karty, id_klienta, numer_maskowany, data_waznosci, limit_kredytowy, dostepne_srodki, waluta, status, data_wydania""";

    public static Karta zWiersza(ResultSet rs, String siec) throws SQLException {
        LocalDate waznosc = rs.getObject("data_waznosci", LocalDate.class);
        return new Karta(
                rs.getLong("id_karty"),
                rs.getLong("id_klienta"),
                siec,
                rs.getString("numer_maskowany"),
                YearMonth.from(waznosc).toString(),
                Kwoty.naGrosze(rs.getBigDecimal("limit_kredytowy")),
                Kwoty.naGrosze(rs.getBigDecimal("dostepne_srodki")),
                rs.getString("waluta"),
                rs.getString("status"),
                CzasUtc.odczytaj(rs, "data_wydania"));
    }
}
