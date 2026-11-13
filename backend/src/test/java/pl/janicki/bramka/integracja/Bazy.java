package pl.janicki.bramka.integracja;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

final class Bazy {

    private Bazy() {
    }

    static Connection polacz(String siec) throws SQLException {
        return DriverManager.getConnection(Srodowisko.jdbcUrl(siec), "bramka", Srodowisko.zmienna("BRAMKA_PASSWORD"));
    }

    static Object wartosc(String siec, String sql, Object... parametry) {
        List<List<Object>> wiersze = wiersze(siec, sql, parametry);
        return wiersze.isEmpty() ? null : wiersze.getFirst().getFirst();
    }

    static long liczba(String siec, String sql, Object... parametry) {
        Object wartosc = wartosc(siec, sql, parametry);
        return wartosc == null ? 0 : ((Number) wartosc).longValue();
    }

    static List<List<Object>> wiersze(String siec, String sql, Object... parametry) {
        try (Connection polaczenie = polacz(siec); PreparedStatement zapytanie = polaczenie.prepareStatement(sql)) {
            for (int i = 0; i < parametry.length; i++) {
                zapytanie.setObject(i + 1, parametry[i]);
            }
            try (ResultSet wynik = zapytanie.executeQuery()) {
                List<List<Object>> wiersze = new ArrayList<>();
                int kolumny = wynik.getMetaData().getColumnCount();
                while (wynik.next()) {
                    List<Object> wiersz = new ArrayList<>();
                    for (int k = 1; k <= kolumny; k++) {
                        wiersz.add(wynik.getObject(k));
                    }
                    wiersze.add(wiersz);
                }
                return wiersze;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(siec + ": " + e.getMessage(), e);
        }
    }

    static int wykonaj(String siec, String sql, Object... parametry) {
        try (Connection polaczenie = polacz(siec); PreparedStatement polecenie = polaczenie.prepareStatement(sql)) {
            for (int i = 0; i < parametry.length; i++) {
                polecenie.setObject(i + 1, parametry[i]);
            }
            return polecenie.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(siec + ": " + e.getMessage(), e);
        }
    }

    static boolean dostepna(String siec) {
        try (Connection polaczenie = polacz(siec)) {
            return polaczenie.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }
}
