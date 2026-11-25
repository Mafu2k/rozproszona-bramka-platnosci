package pl.janicki.bramka.integracja;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlatnosciIT {

    private static Dane.Sklep sklep;
    private static Dane.Karta karta;

    @BeforeAll
    static void przygotuj() {
        Dane.czekajNaBramke();
        sklep = Dane.nowySklep();
        karta = Dane.nowaKarta("MC", Dane.nowyKlient(), 1_000_000);
        assertThat(Dane.autoryzuj(sklep, karta.numer(), Dane.unikalny("rozgrzewka"), 1).status()).isEqualTo(200);
    }

    @Test
    @DisplayName("1. Ten sam idZadania dwa razy: jedna transakcja, ten sam RRN, środki zdjęte raz")
    void powtorzenieZwracaZapisanaDecyzje() {
        long przed = Dane.dostepneSrodki(karta);
        String idZadania = Dane.unikalny("powtorka");

        Api.Odpowiedz pierwsza = Dane.autoryzuj(sklep, karta.numer(), idZadania, 12_345);
        Api.Odpowiedz druga = Dane.autoryzuj(sklep, karta.numer(), idZadania, 12_345);

        assertThat(pierwsza.status()).isEqualTo(200);
        assertThat(pierwsza.tekst("decyzja")).isEqualTo("ZATWIERDZONA");
        assertThat(pierwsza.tresc().get("powtorzona").asBoolean()).isFalse();
        assertThat(druga.tekst("rrn")).isEqualTo(pierwsza.tekst("rrn"));
        assertThat(druga.tresc().get("powtorzona").asBoolean()).isTrue();
        assertThat(Dane.dostepneSrodki(karta)).isEqualTo(przed - 12_345);
        assertThat(Bazy.liczba("MC", "SELECT COUNT(*) FROM transakcja WHERE id_sklepu = ? AND id_zadania = ?", sklep.id(), idZadania))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("2. Równoległe żądania z tym samym idZadania: jedna transakcja i jeden RRN")
    void rownoleglePowtorzeniaDajaJednaTransakcje() throws Exception {
        long przed = Dane.dostepneSrodki(karta);
        String idZadania = Dane.unikalny("rownolegle");
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Api.Odpowiedz>> wyniki = new ArrayList<>();
        try (ExecutorService wykonawca = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 12; i++) {
                wyniki.add(wykonawca.submit(() -> {
                    start.await();
                    return Dane.autoryzuj(sklep, karta.numer(), idZadania, 2_000);
                }));
            }
            start.countDown();
            List<Api.Odpowiedz> odpowiedzi = new ArrayList<>();
            for (Future<Api.Odpowiedz> wynik : wyniki) {
                odpowiedzi.add(wynik.get());
            }
            assertThat(odpowiedzi).allSatisfy(o -> assertThat(o.status()).isEqualTo(200));
            assertThat(odpowiedzi.stream().map(o -> o.tekst("rrn")).distinct()).hasSize(1);
            assertThat(odpowiedzi.stream().filter(o -> !o.tresc().get("powtorzona").asBoolean())).hasSize(1);
        }
        assertThat(Dane.dostepneSrodki(karta)).isEqualTo(przed - 2_000);
    }

    @Test
    @DisplayName("3. Ten sam idZadania z inną kwotą: 409 z kodem 94")
    void innaKwotaToKonflikt94() {
        String idZadania = Dane.unikalny("konflikt");
        assertThat(Dane.autoryzuj(sklep, karta.numer(), idZadania, 500).status()).isEqualTo(200);

        Api.Odpowiedz konflikt = Dane.autoryzuj(sklep, karta.numer(), idZadania, 501);

        assertThat(konflikt.status()).isEqualTo(409);
        assertThat(konflikt.tekst("kod")).isEqualTo("94");
    }

    @Test
    @DisplayName("5. Podwójny zwrot z tym samym kluczem to jeden zwrot, zwrot ponad kwotę jest odrzucony")
    void zwrotyIdempotentneIOgraniczoneKwota() {
        Api.Odpowiedz autoryzacja = Dane.autoryzuj(sklep, karta.numer(), Dane.unikalny("do-zwrotu"), 10_000);
        String rrn = autoryzacja.tekst("rrn");
        long poAutoryzacji = Dane.dostepneSrodki(karta);
        String idZwrotu = Dane.unikalny("zwrot");

        Api.Odpowiedz zwrot = Dane.zwroc(sklep, rrn, idZwrotu, 4_000);
        Api.Odpowiedz powtorka = Dane.zwroc(sklep, rrn, idZwrotu, 4_000);
        Api.Odpowiedz zaDuzo = Dane.zwroc(sklep, rrn, Dane.unikalny("zwrot"), 6_001);
        Api.Odpowiedz reszta = Dane.zwroc(sklep, rrn, Dane.unikalny("zwrot"), 6_000);

        assertThat(zwrot.tekst("decyzja")).isEqualTo("ZATWIERDZONA");
        assertThat(zwrot.tekst("rrnPierwotnej")).isEqualTo(rrn);
        assertThat(powtorka.tekst("rrn")).isEqualTo(zwrot.tekst("rrn"));
        assertThat(powtorka.tresc().get("powtorzona").asBoolean()).isTrue();
        assertThat(zaDuzo.tekst("decyzja")).isEqualTo("ODRZUCONA");
        assertThat(zaDuzo.tekst("kod")).isEqualTo("13");
        assertThat(reszta.tekst("decyzja")).isEqualTo("ZATWIERDZONA");
        assertThat(Dane.dostepneSrodki(karta)).isEqualTo(poAutoryzacji + 10_000);
        Api.Odpowiedz pierwotna = Api.get("/transakcje?rrn=" + rrn).token(Dane.operator("MC")).wyslij();
        assertThat(pierwotna.tresc().get("elementy").get(0).get("status").asString()).isEqualTo("ZWROCONA");
    }

    @Test
    @DisplayName("Walidacja przed routingiem: Luhn i długość (14), kwota (13), klucz API (401)")
    void walidacjaPrzedWezlem() {
        Api.Odpowiedz luhn = Api.post("/platnosci/autoryzacje").kluczApi(sklep.kluczApi())
                .tresc(Map.of("idZadania", "z1", "numerKarty", "5555555555554445", "kwota", 100, "waluta", "PLN")).wyslij();
        Api.Odpowiedz krotki = Api.post("/platnosci/autoryzacje").kluczApi(sklep.kluczApi())
                .tresc(Map.of("idZadania", "z2", "numerKarty", "4111111", "kwota", 100, "waluta", "PLN")).wyslij();
        Api.Odpowiedz kwota = Api.post("/platnosci/autoryzacje").kluczApi(sklep.kluczApi())
                .tresc(Map.of("idZadania", "z3", "numerKarty", karta.numer(), "kwota", 0, "waluta", "PLN")).wyslij();
        Api.Odpowiedz bezKlucza = Api.post("/platnosci/autoryzacje")
                .tresc(Map.of("idZadania", "z4", "numerKarty", karta.numer(), "kwota", 100, "waluta", "PLN")).wyslij();

        assertThat(luhn.status()).isEqualTo(400);
        assertThat(luhn.tekst("kod")).isEqualTo("14");
        assertThat(krotki.tekst("kod")).isEqualTo("14");
        assertThat(kwota.status()).isEqualTo(400);
        assertThat(kwota.tekst("kod")).isEqualTo("13");
        assertThat(bezKlucza.status()).isEqualTo(401);
    }

    @Test
    @DisplayName("Odczyt statusu po (id sklepu, idZadania) po przekroczeniu czasu odpowiedzi")
    void statusZadania() {
        String idZadania = Dane.unikalny("status");
        Api.Odpowiedz autoryzacja = Dane.autoryzuj(sklep, karta.numer(), idZadania, 700);

        Api.Odpowiedz status = Api.get("/platnosci/" + idZadania).kluczApi(sklep.kluczApi()).wyslij();
        Api.Odpowiedz nieznane = Api.get("/platnosci/" + Dane.unikalny("brak")).kluczApi(sklep.kluczApi()).wyslij();

        assertThat(status.status()).isEqualTo(200);
        assertThat(status.tekst("rrn")).isEqualTo(autoryzacja.tekst("rrn"));
        assertThat(nieznane.status()).isEqualTo(404);
    }

    @Test
    @DisplayName("Numery kart wydawane z BIN wydawcy: wspólny prefiks 8 cyfr w zakresie sieci, poprawna suma Luhna")
    void numeryKartZBinuWydawcy() {
        Dane.Karta druga = Dane.nowaKarta("MC", Dane.nowyKlient(), 1_000);
        String bin = karta.numer().substring(0, 8);
        long binLiczbowo = Long.parseLong(bin);

        assertThat(druga.numer()).startsWith(bin).hasSize(16);
        assertThat((binLiczbowo >= 51_000_000 && binLiczbowo <= 55_999_999) || (binLiczbowo >= 22_210_000 && binLiczbowo <= 27_209_999))
                .as("BIN %s w zakresie MasterCard", bin).isTrue();
        assertThat(pl.janicki.bramka.numer.Luhn.poprawny(druga.numer())).isTrue();
    }

    @Test
    @DisplayName("NFR6: baza nie zawiera pełnego numeru karty")
    void bazaNieZawieraNumeruKarty() {
        assertThat(Bazy.liczba("MC", "SELECT COUNT(*) FROM karta WHERE token = ? OR numer_maskowany = ?", karta.numer(), karta.numer()))
                .isZero();
        assertThat(Bazy.wartosc("MC", "SELECT numer_maskowany FROM karta WHERE id_karty = ?", karta.id()))
                .isEqualTo(karta.numer().substring(0, 6) + "******" + karta.numer().substring(12));
    }
}
