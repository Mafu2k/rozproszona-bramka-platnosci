package pl.janicki.bramka.integracja;

import java.time.Duration;
import java.util.function.BooleanSupplier;

final class Czekanie {

    private Czekanie() {
    }

    static Duration az(Duration limit, String opis, BooleanSupplier warunek) {
        long start = System.nanoTime();
        long koniec = start + limit.toNanos();
        RuntimeException ostatni = null;
        while (System.nanoTime() < koniec) {
            try {
                if (warunek.getAsBoolean()) {
                    return Duration.ofNanos(System.nanoTime() - start);
                }
            } catch (RuntimeException e) {
                ostatni = e;
            }
            spij(Duration.ofMillis(500));
        }
        throw new AssertionError("Nie doczekano się (" + limit.toSeconds() + " s): " + opis
                + (ostatni == null ? "" : ", ostatni błąd: " + ostatni.getMessage()));
    }

    static void spij(Duration czas) {
        try {
            Thread.sleep(czas);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
