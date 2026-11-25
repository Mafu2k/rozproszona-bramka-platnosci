package pl.janicki.bramka.sklepy;

import java.sql.ResultSet;
import java.sql.SQLException;

public record Sklep(
        long id,
        String nazwa,
        String nip,
        String kategoriaMcc,
        String url,
        String emailKontaktowy,
        String status,
        boolean maKluczApi) {

    public static final String KOLUMNY = "id_sklepu, nazwa, nip, kategoria_mcc, url, email_kontaktowy, status, hash_klucza_api";

    public static Sklep zWiersza(ResultSet rs) throws SQLException {
        return new Sklep(
                rs.getLong("id_sklepu"),
                rs.getString("nazwa"),
                rs.getString("nip"),
                rs.getString("kategoria_mcc"),
                rs.getString("url"),
                rs.getString("email_kontaktowy"),
                rs.getString("status"),
                rs.getString("hash_klucza_api") != null);
    }
}
