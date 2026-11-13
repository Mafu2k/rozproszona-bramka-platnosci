package pl.janicki.bramka.demonstracja;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

final class Statystyki {

    enum Kanal {
        REST, ISO
    }

    private static final DateTimeFormatter GODZINA = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final class Okno {
        final long poczatekNanos = System.nanoTime();
        final Map<String, LongAdder> sieci = new ConcurrentHashMap<>();
        final Map<String, LongAdder> zatwierdzoneWSieci = new ConcurrentHashMap<>();
        final Map<String, LongAdder> kody = new ConcurrentHashMap<>();
        final ConcurrentLinkedQueue<Long> czasyMs = new ConcurrentLinkedQueue<>();
        final LongAdder autoryzacje = new LongAdder();
        final LongAdder iso = new LongAdder();
        final LongAdder zwroty = new LongAdder();
        final LongAdder powtorzone = new LongAdder();
        final LongAdder pominiete = new LongAdder();
    }

    private final AtomicReference<Okno> okno = new AtomicReference<>(new Okno());
    private final LongAdder wszystkie = new LongAdder();
    private final Map<String, LongAdder> wszystkieKody = new ConcurrentHashMap<>();

    void autoryzacja(Kanal kanal, String siec, String kod, long czasNanos, boolean powtorzona) {
        Okno biezace = okno.get();
        biezace.autoryzacje.increment();
        biezace.sieci.computeIfAbsent(siec, s -> new LongAdder()).increment();
        if ("00".equals(kod)) {
            biezace.zatwierdzoneWSieci.computeIfAbsent(siec, s -> new LongAdder()).increment();
        }
        if (kanal == Kanal.ISO) {
            biezace.iso.increment();
        }
        if (powtorzona) {
            biezace.powtorzone.increment();
        }
        zapiszKod(biezace, kod, czasNanos);
    }

    void zwrot(String kod, long czasNanos) {
        Okno biezace = okno.get();
        biezace.zwroty.increment();
        zapiszKod(biezace, kod, czasNanos);
    }

    void pominieta() {
        okno.get().pominiete.increment();
    }

    private void zapiszKod(Okno biezace, String kod, long czasNanos) {
        biezace.kody.computeIfAbsent(kod, k -> new LongAdder()).increment();
        biezace.czasyMs.add(czasNanos / 1_000_000);
        wszystkie.increment();
        wszystkieKody.computeIfAbsent(kod, k -> new LongAdder()).increment();
    }

    String podsumujOkno() {
        Okno zamkniete = okno.getAndSet(new Okno());
        double sekundy = Math.max(0.001, (System.nanoTime() - zamkniete.poczatekNanos) / 1e9);
        long autoryzacje = zamkniete.autoryzacje.sum();
        long zatwierdzone = zamkniete.zatwierdzoneWSieci.values().stream().mapToLong(LongAdder::sum).sum();
        StringBuilder linia = new StringBuilder();
        linia.append(LocalTime.now().format(GODZINA))
                .append(String.format("  %5.1f tx/s", autoryzacje / sekundy))
                .append(String.format("  zatwierdzone %3.0f%%", autoryzacje == 0 ? 0.0 : 100.0 * zatwierdzone / autoryzacje));
        for (String siec : Uczestnicy.SIECI) {
            long wSieci = licznik(zamkniete.sieci, siec);
            long okWSieci = licznik(zamkniete.zatwierdzoneWSieci, siec);
            linia.append(String.format("  %s %4.1f/s %3.0f%%ok", siec, wSieci / sekundy, wSieci == 0 ? 0.0 : 100.0 * okWSieci / wSieci));
        }
        linia.append("  |  ").append(kody(zamkniete.kody))
                .append("  |  p95 ").append(p95(zamkniete.czasyMs)).append(" ms")
                .append(String.format("  ISO %2.0f%%", autoryzacje == 0 ? 0.0 : 100.0 * zamkniete.iso.sum() / autoryzacje))
                .append("  zwroty ").append(zamkniete.zwroty.sum())
                .append("  powtórki ").append(zamkniete.powtorzone.sum());
        if (zamkniete.pominiete.sum() > 0) {
            linia.append("  pominięte ").append(zamkniete.pominiete.sum());
        }
        return linia.toString();
    }

    String podsumowanieCalosci() {
        return "Wysłano " + wszystkie.sum() + " żądań, kody: " + kody(wszystkieKody);
    }

    private static long licznik(Map<String, LongAdder> liczniki, String klucz) {
        LongAdder licznik = liczniki.get(klucz);
        return licznik == null ? 0 : licznik.sum();
    }

    private static String kody(Map<String, LongAdder> kody) {
        if (kody.isEmpty()) {
            return "brak ruchu";
        }
        return new TreeMap<>(kody).entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<String, LongAdder> e) -> !"00".equals(e.getKey())))
                .map(e -> e.getKey() + ":" + e.getValue().sum())
                .collect(Collectors.joining(" "));
    }

    private static long p95(ConcurrentLinkedQueue<Long> czasy) {
        List<Long> posortowane = new ArrayList<>(czasy);
        if (posortowane.isEmpty()) {
            return 0;
        }
        posortowane.sort(null);
        return posortowane.get((int) Math.ceil(posortowane.size() * 0.95) - 1);
    }
}
