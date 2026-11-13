package pl.janicki.bramka.demonstracja;

import java.io.PrintStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.random.RandomGenerator;

import pl.janicki.bramka.numer.Luhn;

final class Uczestnicy {

    enum Rodzaj {
        ZWYKLA, NISKI_LIMIT, ZABLOKOWANA, WALUTA_EUR
    }

    record Sklep(long id, String nazwa, String kluczApi) {
    }

    record Karta(long id, String numer, String siec, Rodzaj rodzaj) {
    }

    record Platnosc(String numerKarty, String siec, String opis) {
    }

    private record WzorSklepu(String nazwa, String mcc) {
    }

    static final List<String> SIECI = List.of("VISA", "MC", "AMEX");

    private static final Map<String, String> OPERATORZY = Map.of(
            "VISA", "operator.visa",
            "MC", "operator.mastercard",
            "AMEX", "operator.amex");
    private static final List<WzorSklepu> SKLEPY = List.of(
            new WzorSklepu("Elektromarket", "5732"),
            new WzorSklepu("Księgarnia Pod Lipą", "5942"),
            new WzorSklepu("Kawiarnia Ziarno", "5814"));
    private static final List<String[]> KLIENCI = List.of(
            new String[] { "Anna", "Nowak" },
            new String[] { "Piotr", "Kowalski" },
            new String[] { "Katarzyna", "Wiśniewska" },
            new String[] { "Tomasz", "Wójcik" },
            new String[] { "Magdalena", "Kamińska" },
            new String[] { "Michał", "Lewandowski" });
    private static final Map<Rodzaj, Integer> KART_W_SIECI = Map.of(
            Rodzaj.ZWYKLA, 4, Rodzaj.NISKI_LIMIT, 1, Rodzaj.ZABLOKOWANA, 1, Rodzaj.WALUTA_EUR, 1);
    private static final Map<Rodzaj, Long> LIMITY = Map.of(
            Rodzaj.ZWYKLA, 500_000_000L, Rodzaj.NISKI_LIMIT, 30_000L, Rodzaj.ZABLOKOWANA, 1_000_000L, Rodzaj.WALUTA_EUR, 1_000_000L);
    private static final Duration LIMIT_REPLIKACJI = Duration.ofSeconds(120);

    private final List<Sklep> sklepy;
    private final Map<String, Map<Rodzaj, List<Karta>>> karty;

    private Uczestnicy(List<Sklep> sklepy, Map<String, Map<Rodzaj, List<Karta>>> karty) {
        this.sklepy = sklepy;
        this.karty = karty;
    }

    static Uczestnicy przygotuj(KlientBramki bramka, Konfiguracja konfiguracja, RandomGenerator losowanie, PrintStream wyjscie) {
        String admin = bramka.zaloguj("admin", konfiguracja.hasloAdmina());
        List<Sklep> sklepy = new ArrayList<>();
        for (WzorSklepu wzor : SKLEPY) {
            sklepy.add(nowySklep(bramka, admin, wzor, losowanie));
        }
        wyjscie.println("Sklepy: " + sklepy.stream().map(s -> s.nazwa() + " (id " + s.id() + ")").toList());
        List<Long> klienci = new ArrayList<>();
        for (String[] dane : KLIENCI) {
            klienci.add(nowyKlient(bramka, admin, dane[0], dane[1]));
        }
        Map<String, Map<Rodzaj, List<Karta>>> karty = new LinkedHashMap<>();
        for (String siec : SIECI) {
            String operator = bramka.zaloguj(OPERATORZY.get(siec), konfiguracja.hasloOperatorow());
            Map<Rodzaj, List<Karta>> kartySieci = new EnumMap<>(Rodzaj.class);
            for (Rodzaj rodzaj : Rodzaj.values()) {
                List<Karta> lista = new ArrayList<>();
                for (int i = 0; i < KART_W_SIECI.get(rodzaj); i++) {
                    long idKlienta = klienci.get(losowanie.nextInt(klienci.size()));
                    lista.add(nowaKarta(bramka, operator, siec, idKlienta, rodzaj));
                }
                kartySieci.put(rodzaj, lista);
            }
            karty.put(siec, kartySieci);
            wyjscie.println("Karty " + siec + ": " + kartySieci.values().stream().mapToInt(List::size).sum()
                    + " (BIN wydawcy " + kartySieci.get(Rodzaj.ZWYKLA).getFirst().numer().substring(0, 8) + ")");
        }
        Uczestnicy uczestnicy = new Uczestnicy(List.copyOf(sklepy), karty);
        uczestnicy.czekajNaReplikacjeSklepow(bramka, wyjscie);
        return uczestnicy;
    }

    List<Sklep> sklepy() {
        return sklepy;
    }

    int liczbaKart() {
        return karty.values().stream().flatMap(m -> m.values().stream()).mapToInt(List::size).sum();
    }

    Sklep losujSklep(RandomGenerator losowanie) {
        return sklepy.get(losowanie.nextInt(sklepy.size()));
    }

    Platnosc losujPlatnosc(RandomGenerator losowanie) {
        String siec = losujSiec(losowanie);
        double los = losowanie.nextDouble();
        if (los < 0.05) {
            return new Platnosc(karta(siec, Rodzaj.NISKI_LIMIT, losowanie).numer(), siec, "niski limit");
        }
        if (los < 0.07) {
            return new Platnosc(karta(siec, Rodzaj.ZABLOKOWANA, losowanie).numer(), siec, "karta zablokowana");
        }
        if (los < 0.09) {
            return new Platnosc(karta(siec, Rodzaj.WALUTA_EUR, losowanie).numer(), siec, "karta w EUR");
        }
        if (los < 0.11) {
            return new Platnosc(numerSpozaBazy(siec, losowanie), siec, "karta nieznana wydawcy");
        }
        if (los < 0.12) {
            return new Platnosc(zlaSumaKontrolna(karta(siec, Rodzaj.ZWYKLA, losowanie).numer()), siec, "błędna suma Luhna");
        }
        return new Platnosc(karta(siec, Rodzaj.ZWYKLA, losowanie).numer(), siec, "zwykła");
    }

    static long losujKwote(RandomGenerator losowanie, String siec) {
        double mediana = "AMEX".equals(siec) ? 18_000 : 6_500;
        long kwota = Math.round(mediana * Math.exp(losowanie.nextGaussian() * 0.9));
        kwota = Math.clamp(kwota, 199, 1_500_000);
        return kwota / 100 * 100 + (losowanie.nextInt(3) == 0 ? 0 : 99);
    }

    private static String losujSiec(RandomGenerator losowanie) {
        double los = losowanie.nextDouble();
        return los < 0.45 ? "VISA" : los < 0.85 ? "MC" : "AMEX";
    }

    private Karta karta(String siec, Rodzaj rodzaj, RandomGenerator losowanie) {
        List<Karta> lista = karty.get(siec).get(rodzaj);
        return lista.get(losowanie.nextInt(lista.size()));
    }

    private String numerSpozaBazy(String siec, RandomGenerator losowanie) {
        String wzor = karty.get(siec).get(Rodzaj.ZWYKLA).getFirst().numer();
        StringBuilder numer = new StringBuilder(wzor.substring(0, 8));
        while (numer.length() < wzor.length() - 1) {
            numer.append(losowanie.nextInt(10));
        }
        return numer.append(Luhn.cyfraKontrolna(numer.toString())).toString();
    }

    private static String zlaSumaKontrolna(String numer) {
        int ostatnia = numer.charAt(numer.length() - 1) - '0';
        return numer.substring(0, numer.length() - 1) + (ostatnia + 1) % 10;
    }

    private void czekajNaReplikacjeSklepow(KlientBramki bramka, PrintStream wyjscie) {
        wyjscie.println("Czekam, aż nowe sklepy trafią do replik MC i AmEx (odświeżanie co 30 s)");
        for (String siec : List.of("MC", "AMEX")) {
            for (Sklep sklep : sklepy) {
                String numer = karty.get(siec).get(Rodzaj.ZWYKLA).getFirst().numer();
                long koniec = System.nanoTime() + LIMIT_REPLIKACJI.toNanos();
                while (true) {
                    KlientBramki.Odpowiedz odpowiedz = bramka.platnosc("/platnosci/autoryzacje", sklep.kluczApi(), Map.of(
                            "idZadania", "gen-start-" + UUID.randomUUID(), "numerKarty", numer, "kwota", 100, "waluta", "PLN"));
                    if (!odpowiedz.replikacjaWToku()) {
                        break;
                    }
                    if (System.nanoTime() > koniec) {
                        throw new IllegalStateException("Sklep " + sklep.id() + " nie pojawił się w replice " + siec);
                    }
                    KlientBramki.czekaj(Duration.ofSeconds(3));
                }
            }
        }
    }

    private static Sklep nowySklep(KlientBramki bramka, String admin, WzorSklepu wzor, RandomGenerator losowanie) {
        String sufiks = UUID.randomUUID().toString().substring(0, 8);
        KlientBramki.Odpowiedz sklep = bramka.zapisPanelu(admin, "/sklepy", Map.of(
                "nazwa", wzor.nazwa() + " " + sufiks,
                "nip", nip(losowanie),
                "kategoriaMcc", wzor.mcc(),
                "emailKontaktowy", "sklep-" + sufiks + "@example.com"));
        wymagajStatusu(sklep, 201, "nowy sklep");
        long id = sklep.tresc().get("id").asLong();
        KlientBramki.Odpowiedz klucz = bramka.zapisPanelu(admin, "/sklepy/" + id + "/klucz-api", null);
        wymagajStatusu(klucz, 201, "klucz API sklepu " + id);
        return new Sklep(id, wzor.nazwa(), klucz.tekst("kluczApi"));
    }

    private static long nowyKlient(KlientBramki bramka, String admin, String imie, String nazwisko) {
        KlientBramki.Odpowiedz klient = bramka.zapisPanelu(admin, "/klienci", Map.of(
                "imie", imie,
                "nazwisko", nazwisko,
                "email", "klient-" + UUID.randomUUID().toString().substring(0, 12) + "@example.com",
                "kraj", "PL"));
        wymagajStatusu(klient, 201, "nowy klient");
        return klient.tresc().get("id").asLong();
    }

    private static Karta nowaKarta(KlientBramki bramka, String operator, String siec, long idKlienta, Rodzaj rodzaj) {
        Map<String, Object> tresc = new LinkedHashMap<>();
        tresc.put("idKlienta", idKlienta);
        tresc.put("limitKredytowy", LIMITY.get(rodzaj));
        tresc.put("waluta", rodzaj == Rodzaj.WALUTA_EUR ? "EUR" : "PLN");
        KlientBramki.Odpowiedz wydanie = bramka.zapisPaneluPoReplikacji(operator, "/karty", tresc, LIMIT_REPLIKACJI);
        wymagajStatusu(wydanie, 201, "wydanie karty " + siec);
        long id = wydanie.tresc().get("karta").get("id").asLong();
        if (rodzaj == Rodzaj.ZABLOKOWANA) {
            wymagajStatusu(bramka.zapisPanelu(operator, "/karty/" + id + "/blokada", null), 200, "blokada karty " + id);
        }
        return new Karta(id, wydanie.tekst("numerKarty"), siec, rodzaj);
    }

    private static void wymagajStatusu(KlientBramki.Odpowiedz odpowiedz, int status, String operacja) {
        if (odpowiedz.status() != status) {
            throw new IllegalStateException(operacja + ": HTTP " + odpowiedz.status() + " " + odpowiedz.tresc());
        }
    }

    private static String nip(RandomGenerator losowanie) {
        int[] wagi = { 6, 5, 7, 2, 3, 4, 5, 6, 7 };
        while (true) {
            StringBuilder nip = new StringBuilder();
            int suma = 0;
            for (int i = 0; i < wagi.length; i++) {
                int cyfra = i == 0 ? 1 + losowanie.nextInt(9) : losowanie.nextInt(10);
                nip.append(cyfra);
                suma += wagi[i] * cyfra;
            }
            if (suma % 11 != 10) {
                return nip.append(suma % 11).toString();
            }
        }
    }
}
