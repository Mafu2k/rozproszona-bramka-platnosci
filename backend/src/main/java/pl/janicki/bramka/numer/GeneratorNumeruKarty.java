package pl.janicki.bramka.numer;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import pl.janicki.bramka.bledy.BladApi;
import pl.janicki.bramka.konfiguracja.BramkaProperties;

@Component
public class GeneratorNumeruKarty {

    private final SecureRandom losowanie = new SecureRandom();
    private final TablicaBin tablicaBin;
    private final Map<String, String> binyWydawcy;

    @Autowired
    public GeneratorNumeruKarty(TablicaBin tablicaBin, BramkaProperties properties) {
        this(tablicaBin, properties.wydawanie() == null || properties.wydawanie().biny() == null
                ? Map.of() : properties.wydawanie().biny());
    }

    GeneratorNumeruKarty(TablicaBin tablicaBin, Map<String, String> binyWydawcy) {
        this.tablicaBin = tablicaBin;
        this.binyWydawcy = Map.copyOf(binyWydawcy);
    }

    public NumerKarty nowyNumer(String kodSieci) {
        List<ZakresBin> zakresy = tablicaBin.zakresySieci(kodSieci);
        if (zakresy.isEmpty()) {
            throw BladApi.konflikt("BRAK_ZAKRESU_BIN", "Sieć " + kodSieci + " nie ma zdefiniowanych zakresów BIN");
        }
        String binWydawcy = binyWydawcy.get(kodSieci);
        if (binWydawcy == null) {
            return nowyNumer(zakresy.get(losowanie.nextInt(zakresy.size())));
        }
        if (!binWydawcy.matches("^[0-9]{8}$")) {
            throw new IllegalStateException("BIN wydawcy sieci " + kodSieci + " musi mieć 8 cyfr");
        }
        long bin = Long.parseLong(binWydawcy);
        ZakresBin zakres = zakresy.stream()
                .filter(z -> bin >= z.binOd() && bin <= z.binDo())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("BIN wydawcy " + binWydawcy + " nie należy do zakresów sieci " + kodSieci));
        return zPrefiksu(binWydawcy, zakres.dlugoscPan());
    }

    NumerKarty nowyNumer(ZakresBin zakres) {
        long bin = zakres.binOd() + (long) (losowanie.nextDouble() * (zakres.binDo() - zakres.binOd() + 1));
        return zPrefiksu(Long.toString(bin), zakres.dlugoscPan());
    }

    private NumerKarty zPrefiksu(String prefiks, int dlugosc) {
        StringBuilder cyfry = new StringBuilder(prefiks);
        while (cyfry.length() < dlugosc - 1) {
            cyfry.append(losowanie.nextInt(10));
        }
        cyfry.append(Luhn.cyfraKontrolna(cyfry.toString()));
        return NumerKarty.z(cyfry.toString());
    }
}
