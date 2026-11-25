package pl.janicki.bramka.integracja;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ObciazenieIT {

    private record Pomiar(Api.Odpowiedz odpowiedz, String numerKarty, long kwota, long milisekundy) {
    }

    @Test
    @DisplayName("4 / NFR2 (6.2): 100 równoległych autoryzacji, saldo nigdy ujemne, sumy się zgadzają, średnio < 1 s")
    void stoRownoleglychAutoryzacji() throws Exception {
        Dane.czekajNaBramke();
        Dane.Sklep sklep = Dane.nowySklep();
        long klient = Dane.nowyKlient();
        Dane.Karta waska = Dane.nowaKarta("MC", klient, 50_000);
        Dane.Karta szeroka1 = Dane.nowaKarta("MC", klient, 10_000_000);
        Dane.Karta szeroka2 = Dane.nowaKarta("MC", klient, 10_000_000);
        assertThat(Dane.autoryzuj(sklep, szeroka1.numer(), Dane.unikalny("rozgrzewka"), 1).status()).isEqualTo(200);

        List<Dane.Karta> plan = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            plan.add(waska);
        }
        for (int i = 0; i < 20; i++) {
            plan.add(szeroka1);
            plan.add(szeroka2);
        }
        Collections.shuffle(plan);

        CountDownLatch start = new CountDownLatch(1);
        List<Future<Pomiar>> zadania = new ArrayList<>();
        try (ExecutorService wykonawca = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Dane.Karta karta : plan) {
                long kwota = karta == waska ? 1_000 : 100;
                zadania.add(wykonawca.submit(() -> {
                    start.await();
                    long poczatek = System.nanoTime();
                    Api.Odpowiedz odpowiedz = Dane.autoryzuj(sklep, karta.numer(), Dane.unikalny("obc"), kwota);
                    return new Pomiar(odpowiedz, karta.numer(), kwota, (System.nanoTime() - poczatek) / 1_000_000);
                }));
            }
            start.countDown();
            List<Pomiar> pomiary = new ArrayList<>();
            for (Future<Pomiar> zadanie : zadania) {
                pomiary.add(zadanie.get());
            }

            assertThat(pomiary).allSatisfy(p -> assertThat(p.odpowiedz().status()).as(p.odpowiedz().toString()).isEqualTo(200));
            long zatwierdzoneWaska = pomiary.stream()
                    .filter(p -> p.numerKarty().equals(waska.numer()) && "00".equals(p.odpowiedz().tekst("kod"))).count();
            long odrzuconeWaska = pomiary.stream()
                    .filter(p -> p.numerKarty().equals(waska.numer()) && "51".equals(p.odpowiedz().tekst("kod"))).count();
            assertThat(zatwierdzoneWaska).isEqualTo(50);
            assertThat(odrzuconeWaska).isEqualTo(10);
            assertThat(Dane.dostepneSrodki(waska)).isZero();
            for (Dane.Karta karta : List.of(waska, szeroka1, szeroka2)) {
                BigDecimal suma = (BigDecimal) Bazy.wartosc("MC",
                        "SELECT NVL(SUM(kwota), 0) FROM transakcja WHERE id_karty = ? AND status = 'AUTORYZOWANA'", karta.id());
                BigDecimal limit = (BigDecimal) Bazy.wartosc("MC", "SELECT limit_kredytowy FROM karta WHERE id_karty = ?", karta.id());
                BigDecimal dostepne = (BigDecimal) Bazy.wartosc("MC", "SELECT dostepne_srodki FROM karta WHERE id_karty = ?", karta.id());
                assertThat(dostepne.signum()).isGreaterThanOrEqualTo(0);
                assertThat(limit.subtract(dostepne)).isEqualByComparingTo(suma);
            }
            List<Long> czasy = pomiary.stream().map(Pomiar::milisekundy).sorted().toList();
            double srednia = czasy.stream().mapToLong(Long::longValue).average().orElseThrow();
            long p95 = czasy.get((int) Math.ceil(czasy.size() * 0.95) - 1);
            System.out.printf("NFR2: 100 równoległych autoryzacji, średnio %.0f ms, p95 %d ms, max %d ms%n",
                    srednia, p95, czasy.getLast());
            assertThat(srednia).isLessThan(1000);
        }
    }
}
