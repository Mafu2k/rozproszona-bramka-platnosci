package pl.janicki.bramka.platnosci;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Component;

import pl.janicki.bramka.wezly.Wezel;
import pl.janicki.bramka.wspolne.Kwoty;

@Component
public class ProceduryPlatnosci {

    private static final String AUTORYZUJ = "{call pkg_autoryzacja.autoryzuj(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}";
    private static final String ZWROC = "{call pkg_autoryzacja.zwroc(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}";

    public WynikProcedury autoryzuj(Wezel wezel, String token, long idSklepu, String idZadania, long kwotaWGroszach, String waluta) {
        return wywolaj(wezel, AUTORYZUJ, token, idSklepu, idZadania, kwotaWGroszach, waluta);
    }

    public WynikProcedury zwroc(Wezel wezel, String rrnPierwotnej, long idSklepu, String idZadania, long kwotaWGroszach, String waluta) {
        return wywolaj(wezel, ZWROC, rrnPierwotnej, idSklepu, idZadania, kwotaWGroszach, waluta);
    }

    private WynikProcedury wywolaj(Wezel wezel, String wywolanie, String identyfikator, long idSklepu, String idZadania,
            long kwotaWGroszach, String waluta) {
        return wezel.jdbcTemplate().execute((ConnectionCallback<WynikProcedury>) polaczenie -> {
            try (CallableStatement procedura = polaczenie.prepareCall(wywolanie)) {
                procedura.setString(1, identyfikator);
                procedura.setLong(2, idSklepu);
                procedura.setString(3, idZadania);
                procedura.setBigDecimal(4, Kwoty.zGroszy(kwotaWGroszach));
                procedura.setString(5, waluta);
                procedura.registerOutParameter(6, Types.NUMERIC);
                procedura.registerOutParameter(7, Types.VARCHAR);
                procedura.registerOutParameter(8, Types.VARCHAR);
                procedura.registerOutParameter(9, Types.VARCHAR);
                procedura.registerOutParameter(10, Types.TIMESTAMP);
                procedura.registerOutParameter(11, Types.VARCHAR);
                procedura.registerOutParameter(12, Types.NUMERIC);
                procedura.setQueryTimeout(wezel.jdbcTemplate().getQueryTimeout());
                procedura.execute();
                return odczytaj(procedura);
            }
        });
    }

    private static WynikProcedury odczytaj(CallableStatement procedura) throws SQLException {
        LocalDateTime data = procedura.getObject(10, LocalDateTime.class);
        BigDecimal powtorzona = procedura.getBigDecimal(12);
        return new WynikProcedury(
                procedura.getString(7),
                procedura.getString(8),
                procedura.getString(9),
                data == null ? null : data.atOffset(ZoneOffset.UTC),
                procedura.getString(11),
                powtorzona != null && powtorzona.intValue() == 1);
    }
}
