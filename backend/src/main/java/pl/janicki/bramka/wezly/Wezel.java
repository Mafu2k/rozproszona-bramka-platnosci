package pl.janicki.bramka.wezly;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import pl.janicki.bramka.bledy.BledyBazy;

public record Wezel(
        int nr,
        String kodSieci,
        DataSource zrodloDanych,
        JdbcTemplate jdbcTemplate,
        JdbcClient jdbc,
        TransactionTemplate transakcja) {

    public static final int NR_WEZLA_GLOWNEGO = 1;

    public boolean glowny() {
        return nr == NR_WEZLA_GLOWNEGO;
    }

    public RuntimeException przetlumacz(Throwable blad) {
        return BledyBazy.przetlumacz(blad, kodSieci);
    }

    public boolean bladLaczaDoInnegoWezla(Throwable blad) {
        return BledyBazy.niedostepnaZdalnaSiec(blad).or(() -> BledyBazy.zgloszonaNiedostepnosc(blad))
                .filter(siec -> !siec.equals(kodSieci)).isPresent();
    }

    public void odswiezPolaczenia() {
        if (zrodloDanych instanceof HikariDataSource pula && pula.getHikariPoolMXBean() != null) {
            pula.getHikariPoolMXBean().softEvictConnections();
        }
    }
}
