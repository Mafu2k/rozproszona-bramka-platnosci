package pl.janicki.bramka.numer;

public record ZakresBin(long binOd, long binDo, int dlugoscPan, String kodSieci, int nrWezla) {

    public boolean obejmuje(NumerKarty numer) {
        long bin = numer.bin8();
        return bin >= binOd && bin <= binDo && numer.dlugosc() == dlugoscPan;
    }
}
