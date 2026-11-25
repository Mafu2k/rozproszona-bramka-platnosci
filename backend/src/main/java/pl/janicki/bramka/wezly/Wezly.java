package pl.janicki.bramka.wezly;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import pl.janicki.bramka.bledy.BladApi;
import pl.janicki.bramka.bledy.BledyBazy;
import pl.janicki.bramka.konfiguracja.BramkaProperties;

@Component
public class Wezly implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(Wezly.class);
    private static final Duration LIMIT_ZAPYTANIA_ROZPROSZONEGO = Duration.ofSeconds(8);
    private static final Duration PRZERWA_PO_AWARII = Duration.ofSeconds(10);

    private final Map<Integer, Wezel> poNumerze = new LinkedHashMap<>();
    private final Map<String, Wezel> poKodzie = new LinkedHashMap<>();
    private final List<HikariDataSource> pule = new ArrayList<>();
    private final ExecutorService wykonawca = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, Instant> niedostepneDo = new ConcurrentHashMap<>();

    public Wezly(BramkaProperties properties) {
        BramkaProperties.Baza baza = properties.baza();
        for (BramkaProperties.WezelProperties wezel : properties.wezly()) {
            HikariDataSource pula = utworzPule(wezel, baza);
            pule.add(pula);
            JdbcTemplate jdbcTemplate = new JdbcTemplate(pula);
            jdbcTemplate.setQueryTimeout((int) baza.czasZapytania().toSeconds());
            TransactionTemplate transakcja = new TransactionTemplate(new DataSourceTransactionManager(pula));
            Wezel w = new Wezel(wezel.nr(), wezel.kod(), pula, jdbcTemplate, JdbcClient.create(jdbcTemplate), transakcja);
            poNumerze.put(w.nr(), w);
            poKodzie.put(w.kodSieci(), w);
        }
    }

    private static HikariDataSource utworzPule(BramkaProperties.WezelProperties wezel, BramkaProperties.Baza baza) {
        HikariConfig konfiguracja = new HikariConfig();
        konfiguracja.setPoolName("wezel-" + wezel.kod());
        konfiguracja.setJdbcUrl(wezel.url());
        konfiguracja.setUsername(baza.uzytkownik());
        konfiguracja.setPassword(baza.haslo());
        konfiguracja.setMaximumPoolSize(baza.rozmiarPuli());
        konfiguracja.setMinimumIdle(2);
        konfiguracja.setConnectionTimeout(baza.czasPolaczenia().toMillis());
        konfiguracja.setValidationTimeout(1000);
        konfiguracja.setKeepaliveTime(30_000);
        konfiguracja.setInitializationFailTimeout(-1);
        konfiguracja.setAutoCommit(true);
        Properties sterownik = new Properties();
        sterownik.setProperty("oracle.net.CONNECT_TIMEOUT", String.valueOf(baza.czasPolaczenia().toMillis()));
        sterownik.setProperty("oracle.jdbc.ReadTimeout", String.valueOf(baza.czasZapytania().plusSeconds(5).toMillis()));
        konfiguracja.setDataSourceProperties(sterownik);
        return new HikariDataSource(konfiguracja);
    }

    public Wezel glowny() {
        return wezel(Wezel.NR_WEZLA_GLOWNEGO);
    }

    public Wezel wezel(int nr) {
        Wezel wezel = poNumerze.get(nr);
        if (wezel == null) {
            throw BladApi.nieZnaleziono("Nieznany węzeł " + nr);
        }
        return wezel;
    }

    public Optional<Wezel> wezelSieci(String kodSieci) {
        return Optional.ofNullable(poKodzie.get(kodSieci));
    }

    public Optional<Wezel> wezelDlaIdentyfikatora(long id) {
        int reszta = (int) (id % 10);
        return Optional.ofNullable(poNumerze.get(reszta == 0 ? 10 : reszta));
    }

    public Optional<Wezel> wezelDlaRrn(String rrn) {
        if (rrn == null || rrn.length() != 12 || !Character.isDigit(rrn.charAt(4))) {
            return Optional.empty();
        }
        int cyfra = rrn.charAt(4) - '0';
        return Optional.ofNullable(poNumerze.get(cyfra == 0 ? 10 : cyfra));
    }

    public Collection<Wezel> wszystkie() {
        return poNumerze.values();
    }

    public <T> WynikZWezlow<T> zapytaj(Collection<Wezel> wezly, Function<Wezel, T> zapytanie) {
        Map<String, Future<T>> zadania = new LinkedHashMap<>();
        for (Wezel wezel : wezly) {
            zadania.put(wezel.kodSieci(), wykonawca.submit(() -> zapytanie.apply(wezel)));
        }
        Map<String, T> wyniki = new LinkedHashMap<>();
        List<String> brakujace = new ArrayList<>();
        for (Map.Entry<String, Future<T>> zadanie : zadania.entrySet()) {
            try {
                wyniki.put(zadanie.getKey(), zadanie.getValue().get(LIMIT_ZAPYTANIA_ROZPROSZONEGO.toMillis(), TimeUnit.MILLISECONDS));
            } catch (ExecutionException e) {
                if (BledyBazy.wezelNiedostepny(e.getCause()) || niedostepnyPrzezBladApi(e.getCause())) {
                    log.warn("Węzeł {} niedostępny: {}", zadanie.getKey(), e.getCause().getMessage());
                    brakujace.add(zadanie.getKey());
                } else {
                    throw BledyBazy.przetlumacz(e.getCause(), zadanie.getKey());
                }
            } catch (TimeoutException e) {
                zadanie.getValue().cancel(true);
                log.warn("Węzeł {} nie odpowiedział w czasie", zadanie.getKey());
                brakujace.add(zadanie.getKey());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        return new WynikZWezlow<>(wyniki, brakujace);
    }

    public <T> WynikZWezlow<T> zapytajWszystkie(Function<Wezel, T> zapytanie) {
        return zapytaj(wszystkie(), zapytanie);
    }

    public <T> OdczytWspolny<T> czytajDaneWspolne(Function<Wezel, T> zapytanie) {
        List<Wezel> kolejnosc = new ArrayList<>(wszystkie());
        kolejnosc.sort(Comparator.comparing(this::niedawnoNiedostepny));
        for (Wezel wezel : kolejnosc) {
            try {
                T wartosc = zapytanie.apply(wezel);
                niedostepneDo.remove(wezel.kodSieci());
                return new OdczytWspolny<>(wartosc, wezel.kodSieci());
            } catch (RuntimeException e) {
                if (!BledyBazy.wezelNiedostepny(e) && !niedostepnyPrzezBladApi(e)) {
                    throw wezel.przetlumacz(e);
                }
                niedostepneDo.put(wezel.kodSieci(), Instant.now().plus(PRZERWA_PO_AWARII));
                log.warn("Odczyt danych wspólnych z węzła {} nieudany, próbuję kolejnej repliki", wezel.kodSieci());
            }
        }
        throw BladApi.siecNiedostepna(glowny().kodSieci());
    }

    private boolean niedawnoNiedostepny(Wezel wezel) {
        Instant koniecPrzerwy = niedostepneDo.get(wezel.kodSieci());
        return koniecPrzerwy != null && koniecPrzerwy.isAfter(Instant.now());
    }

    private static boolean niedostepnyPrzezBladApi(Throwable blad) {
        return blad instanceof BladApi bladApi && "91".equals(bladApi.kod());
    }

    @Override
    public void destroy() {
        wykonawca.shutdownNow();
        pule.forEach(HikariDataSource::close);
    }
}
