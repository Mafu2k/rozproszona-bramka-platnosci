package pl.janicki.bramka.klienci;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;

import pl.janicki.bramka.wspolne.CzasUtc;

public record Klient(
        long id,
        String imie,
        String nazwisko,
        String email,
        String telefon,
        String kraj,
        OffsetDateTime dataRejestracji,
        String status) {

    public static final String KOLUMNY = "id_klienta, imie, nazwisko, email, telefon, kraj, data_rejestracji, status";

    public static Klient zWiersza(ResultSet rs) throws SQLException {
        return new Klient(
                rs.getLong("id_klienta"),
                rs.getString("imie"),
                rs.getString("nazwisko"),
                rs.getString("email"),
                rs.getString("telefon"),
                rs.getString("kraj").trim(),
                CzasUtc.odczytaj(rs, "data_rejestracji"),
                rs.getString("status"));
    }
}
