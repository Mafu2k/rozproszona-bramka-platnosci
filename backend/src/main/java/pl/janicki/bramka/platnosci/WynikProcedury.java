package pl.janicki.bramka.platnosci;

import java.time.OffsetDateTime;

public record WynikProcedury(
        String rrn,
        String status,
        String kod,
        OffsetDateTime data,
        String maska,
        boolean powtorzona) {
}
