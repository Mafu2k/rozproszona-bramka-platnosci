package pl.janicki.bramka.demonstracja;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.random.RandomGenerator;

import org.jpos.iso.ISOException;

public final class GeneratorRuchu {

    private static final double UDZIAL_ZWROTOW = 0.03;
    private static final double UDZIAL_POWTOREN = 0.01;
    private static final int MAKS_ROWNOLEGLYCH = 300;
    private static final int POLACZENIA_ISO = 8;
    private static final long OKNO_STATYSTYK_S = 5;
    private static final String BRAK_POLACZENIA = "BRAK_POLACZENIA";

    private record Zatwierdzona(Uczestnicy.Sklep sklep, String rrn, long kwota, Statystyki.Kanal kanal) {
    }

    private record ZadanieRest(Uczestnicy.Sklep sklep, String siec, Map<String, Object> tresc) {
    }

    private final Konfiguracja konfiguracja;
    private final KlientBramki bramka;
    private final KlientIso8583 iso;
    private final Uczestnicy uczestnicy;
    private final PrintStream wyjscie;
    private final Statystyki statystyki = new Statystyki();
    private final Semaphore wolneMiejsca = new Semaphore(MAKS_ROWNOLEGLYCH);
    private final BlockingQueue<Zatwierdzona> doZwrotu = new ArrayBlockingQueue<>(2_000);
    private final AtomicReference<ZadanieRest> ostatnieZadanie = new AtomicReference<>();

    private GeneratorRuchu(Konfiguracja konfiguracja, KlientBramki bramka, KlientIso8583 iso, Uczestnicy uczestnicy,
            PrintStream wyjscie) {
        this.konfiguracja = konfiguracja;
        this.bramka = bramka;
        this.iso = iso;
        this.uczestnicy = uczestnicy;
        this.wyjscie = wyjscie;
    }

    public static void main(String[] argumenty) throws IOException {
        PrintStream wyjscie = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        Konfiguracja konfiguracja = Konfiguracja.zeSrodowiska(System.getenv());
        KlientBramki bramka = new KlientBramki(konfiguracja.adresBramki());
        wyjscie.println("Generator ruchu: " + konfiguracja.tps() + " tx/s, ISO 8583: " + Math.round(konfiguracja.udzialIso() * 100)
                + "%, bramka " + konfiguracja.adresBramki());
        bramka.czekajNaGotowosc(konfiguracja.hasloAdmina(), Duration.ofMinutes(10), wyjscie);
        Uczestnicy uczestnicy = Uczestnicy.przygotuj(bramka, konfiguracja, ThreadLocalRandom.current(), wyjscie);
        wyjscie.println("Gotowe: " + uczestnicy.sklepy().size() + " sklepy, " + uczestnicy.liczbaKart() + " kart. Start ruchu.");
        try (KlientIso8583 iso = new KlientIso8583(konfiguracja.hostIso(), konfiguracja.portIso(), POLACZENIA_ISO)) {
            new GeneratorRuchu(konfiguracja, bramka, iso, uczestnicy, wyjscie).uruchom();
        }
    }

    private void uruchom() {
        ScheduledExecutorService raporty = Executors.newSingleThreadScheduledExecutor();
        raporty.scheduleAtFixedRate(() -> wyjscie.println(statystyki.podsumujOkno()), OKNO_STATYSTYK_S, OKNO_STATYSTYK_S,
                TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> wyjscie.println(statystyki.podsumowanieCalosci())));
        double sredniOdstepNanos = 1e9 / konfiguracja.tps();
        try (ExecutorService wykonawca = Executors.newVirtualThreadPerTaskExecutor()) {
            long nastepne = System.nanoTime();
            while (!Thread.currentThread().isInterrupted()) {
                nastepne += (long) (-Math.log(1 - ThreadLocalRandom.current().nextDouble()) * sredniOdstepNanos);
                long teraz = System.nanoTime();
                if (teraz - nastepne > 1_000_000_000L) {
                    nastepne = teraz;
                }
                LockSupport.parkNanos(nastepne - teraz);
                if (!wolneMiejsca.tryAcquire()) {
                    statystyki.pominieta();
                    continue;
                }
                wykonawca.submit(() -> {
                    try {
                        jednaOperacja(ThreadLocalRandom.current());
                    } finally {
                        wolneMiejsca.release();
                    }
                });
            }
        } finally {
            raporty.shutdownNow();
        }
    }

    private void jednaOperacja(RandomGenerator losowanie) {
        double los = losowanie.nextDouble();
        if (los < UDZIAL_ZWROTOW) {
            Zatwierdzona transakcja = doZwrotu.poll();
            if (transakcja != null) {
                zwroc(transakcja, losowanie);
                return;
            }
        } else if (los < UDZIAL_ZWROTOW + UDZIAL_POWTOREN) {
            ZadanieRest ostatnie = ostatnieZadanie.get();
            if (ostatnie != null) {
                autoryzujRest(ostatnie);
                return;
            }
        }
        Uczestnicy.Platnosc platnosc = uczestnicy.losujPlatnosc(losowanie);
        Uczestnicy.Sklep sklep = uczestnicy.losujSklep(losowanie);
        long kwota = Uczestnicy.losujKwote(losowanie, platnosc.siec());
        if (losowanie.nextDouble() < konfiguracja.udzialIso()) {
            autoryzujIso(sklep, platnosc, kwota);
        } else {
            ZadanieRest zadanie = new ZadanieRest(sklep, platnosc.siec(), Map.of(
                    "idZadania", "gen-" + UUID.randomUUID(),
                    "numerKarty", platnosc.numerKarty(),
                    "kwota", kwota,
                    "waluta", "PLN"));
            ostatnieZadanie.set(zadanie);
            autoryzujRest(zadanie);
        }
    }

    private void autoryzujRest(ZadanieRest zadanie) {
        long start = System.nanoTime();
        try {
            KlientBramki.Odpowiedz odpowiedz = bramka.platnosc("/platnosci/autoryzacje", zadanie.sklep().kluczApi(), zadanie.tresc());
            String kod = odpowiedz.tekst("kod");
            boolean powtorzona = odpowiedz.status() == 200 && odpowiedz.tresc().get("powtorzona").asBoolean();
            statystyki.autoryzacja(Statystyki.Kanal.REST, zadanie.siec(), kod, System.nanoTime() - start, powtorzona);
            if ("00".equals(kod) && !powtorzona) {
                zapamietajDoZwrotu(new Zatwierdzona(zadanie.sklep(), odpowiedz.tekst("rrn"), ((Number) zadanie.tresc().get("kwota")).longValue(),
                        Statystyki.Kanal.REST));
            }
        } catch (UncheckedIOException e) {
            statystyki.autoryzacja(Statystyki.Kanal.REST, zadanie.siec(), BRAK_POLACZENIA, System.nanoTime() - start, false);
        }
    }

    private void autoryzujIso(Uczestnicy.Sklep sklep, Uczestnicy.Platnosc platnosc, long kwota) {
        long start = System.nanoTime();
        try {
            KlientIso8583.Odpowiedz odpowiedz = iso.autoryzuj(sklep.id(), platnosc.numerKarty(), kwota);
            statystyki.autoryzacja(Statystyki.Kanal.ISO, platnosc.siec(), odpowiedz.kod(), System.nanoTime() - start, false);
            if ("00".equals(odpowiedz.kod())) {
                zapamietajDoZwrotu(new Zatwierdzona(sklep, odpowiedz.rrn(), kwota, Statystyki.Kanal.ISO));
            }
        } catch (IOException | ISOException e) {
            statystyki.autoryzacja(Statystyki.Kanal.ISO, platnosc.siec(), BRAK_POLACZENIA, System.nanoTime() - start, false);
        }
    }

    private void zwroc(Zatwierdzona transakcja, RandomGenerator losowanie) {
        long kwota = losowanie.nextInt(10) < 7 ? transakcja.kwota() : Math.max(1, transakcja.kwota() / 2);
        long start = System.nanoTime();
        try {
            String kod;
            if (transakcja.kanal() == Statystyki.Kanal.ISO) {
                kod = iso.wycofaj(transakcja.sklep().id(), transakcja.rrn(), transakcja.kwota()).kod();
            } else {
                kod = bramka.platnosc("/platnosci/zwroty", transakcja.sklep().kluczApi(), Map.of(
                        "idZadania", "gen-zwrot-" + UUID.randomUUID(),
                        "rrnPierwotnej", transakcja.rrn(),
                        "kwota", kwota,
                        "waluta", "PLN")).tekst("kod");
            }
            statystyki.zwrot(kod, System.nanoTime() - start);
        } catch (IOException | ISOException | UncheckedIOException e) {
            statystyki.zwrot(BRAK_POLACZENIA, System.nanoTime() - start);
        }
    }

    private void zapamietajDoZwrotu(Zatwierdzona transakcja) {
        while (!doZwrotu.offer(transakcja)) {
            doZwrotu.poll();
        }
    }
}
