package pl.janicki.bramka.wspolne;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

public final class CzasUtc {

    private CzasUtc() {
    }

    public static OffsetDateTime odczytaj(ResultSet rs, String kolumna) throws SQLException {
        LocalDateTime wartosc = rs.getObject(kolumna, LocalDateTime.class);
        return wartosc == null ? null : wartosc.atOffset(ZoneOffset.UTC);
    }

    public static LocalDateTime doBazy(OffsetDateTime czas) {
        return czas == null ? null : czas.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    public static OffsetDateTime teraz() {
        return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
    }
}
