package pl.janicki.bramka.iso8583;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISORequestListener;
import org.jpos.iso.ISOSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import pl.janicki.bramka.bledy.BladApi;
import pl.janicki.bramka.platnosci.PlatnosciService;
import pl.janicki.bramka.platnosci.WynikPlatnosci;
import pl.janicki.bramka.platnosci.ZadanieAutoryzacji;
import pl.janicki.bramka.platnosci.ZadanieZwrotu;
import pl.janicki.bramka.uwierzytelnianie.RejestrSklepow;
import pl.janicki.bramka.uwierzytelnianie.SklepUwierzytelniony;

@Component
public class ObslugaIso8583 implements ISORequestListener {

    private static final Logger log = LoggerFactory.getLogger(ObslugaIso8583.class);

    private static final Map<String, String> WALUTY = Map.of(
            "985", "PLN",
            "978", "EUR",
            "840", "USD",
            "826", "GBP",
            "203", "CZK");

    private static final int[] POLA_WRAZLIWE = { 2, 14, 35, 45, 52, 55 };

    private final PlatnosciService platnosci;
    private final RejestrSklepow rejestrSklepow;
    private final MeterRegistry rejestrMetryk;

    public ObslugaIso8583(PlatnosciService platnosci, RejestrSklepow rejestrSklepow, MeterRegistry rejestrMetryk) {
        this.platnosci = platnosci;
        this.rejestrSklepow = rejestrSklepow;
        this.rejestrMetryk = rejestrMetryk;
    }

    @Override
    public boolean process(ISOSource zrodlo, ISOMsg komunikat) {
        try {
            ISOMsg odpowiedz = (ISOMsg) komunikat.clone();
            odpowiedz.setResponseMTI();
            for (int pole : POLA_WRAZLIWE) {
                odpowiedz.unset(pole);
            }
            obsluz(komunikat, odpowiedz);
            Counter.builder("bramka.iso8583.komunikaty")
                    .description("Komunikaty ISO 8583 według typu i kodu odpowiedzi")
                    .tag("mti", komunikat.getMTI())
                    .tag("kod", String.valueOf(odpowiedz.getString(39)))
                    .register(rejestrMetryk)
                    .increment();
            zrodlo.send(odpowiedz);
            return true;
        } catch (ISOException | IOException e) {
            log.warn("Nie udało się obsłużyć komunikatu ISO 8583: {}", e.getMessage());
            return false;
        }
    }

    void obsluz(ISOMsg komunikat, ISOMsg odpowiedz) throws ISOException {
        String mti = komunikat.getMTI();
        Optional<SklepUwierzytelniony> sklep = sklep(komunikat.getString(42));
        if (sklep.isEmpty() || !sklep.get().aktywny()) {
            odpowiedz.set(39, "03");
            return;
        }
        String waluta = WALUTY.get(komunikat.getString(49));
        if (waluta == null || !komunikat.hasField(4)) {
            odpowiedz.set(39, "13");
            return;
        }
        long kwota = Long.parseLong(komunikat.getString(4));
        String idZadania = "ISO-" + komunikat.getString(42).trim() + "-" + komunikat.getString(11) + "-" + komunikat.getString(7);
        try {
            WynikPlatnosci wynik = switch (mti) {
                case "0100", "0200" -> platnosci.autoryzuj(sklep.get().id(),
                        new ZadanieAutoryzacji(idZadania, komunikat.getString(2), kwota, waluta));
                case "0400", "0420" -> platnosci.zwroc(sklep.get().id(),
                        new ZadanieZwrotu(idZadania, komunikat.getString(37), kwota, waluta));
                default -> null;
            };
            if (wynik == null) {
                odpowiedz.set(39, "12");
                return;
            }
            odpowiedz.set(39, wynik.kod());
            if (wynik.rrn() != null) {
                odpowiedz.set(37, wynik.rrn());
            }
            if ("00".equals(wynik.kod()) && wynik.rrn() != null) {
                odpowiedz.set(38, wynik.rrn().substring(6));
            }
        } catch (BladApi blad) {
            odpowiedz.set(39, kodIso(blad.kod()));
        } catch (RuntimeException blad) {
            log.error("Błąd przetwarzania komunikatu ISO 8583 {}: {}", mti, blad.getMessage());
            odpowiedz.set(39, "96");
        }
    }

    private Optional<SklepUwierzytelniony> sklep(String identyfikatorAkceptanta) {
        if (identyfikatorAkceptanta == null || !identyfikatorAkceptanta.trim().matches("\\d{1,15}")) {
            return Optional.empty();
        }
        try {
            return rejestrSklepow.poIdentyfikatorze(Long.parseLong(identyfikatorAkceptanta.trim()));
        } catch (BladApi blad) {
            return Optional.empty();
        }
    }

    private static String kodIso(String kodBledu) {
        if (kodBledu != null && kodBledu.matches("\\d{2}")) {
            return kodBledu;
        }
        return "REPLIKACJA_W_TOKU".equals(kodBledu) ? "91" : "96";
    }
}
