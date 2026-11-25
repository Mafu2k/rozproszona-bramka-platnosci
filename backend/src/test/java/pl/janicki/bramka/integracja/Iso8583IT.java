package pl.janicki.bramka.integracja;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

import org.jpos.iso.ISOMsg;
import org.jpos.iso.channel.ASCIIChannel;
import org.jpos.iso.packager.ISO87APackager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class Iso8583IT {

    private static ISOMsg zapytaj(ISOMsg komunikat) throws Exception {
        ASCIIChannel kanal = new ASCIIChannel("localhost", Integer.getInteger("iso8583.port", 8583), new ISO87APackager());
        kanal.setTimeout(30_000);
        kanal.connect();
        try {
            kanal.send(komunikat);
            return kanal.receive();
        } finally {
            kanal.disconnect();
        }
    }

    private static ISOMsg komunikat(String mti, String numerKarty, long kwota, long idSklepu, String stan, String czas) throws Exception {
        ISOMsg m = new ISOMsg(mti);
        if (numerKarty != null) {
            m.set(2, numerKarty);
        }
        m.set(3, "000000");
        m.set(4, String.format("%012d", kwota));
        m.set(7, czas);
        m.set(11, stan);
        m.set(41, "TERM0001");
        m.set(42, String.format("%015d", idSklepu));
        m.set(49, "985");
        return m;
    }

    @Test
    @DisplayName("jPOS ISO 8583: 0100/0110 autoryzacja idempotentna po DE42+DE11+DE7, 0400/0410 wycofanie po DE37")
    void autoryzacjaIWycofanieIso8583() throws Exception {
        Dane.czekajNaBramke();
        Dane.Sklep sklep = Dane.nowySklep();
        Dane.Karta karta = Dane.nowaKarta("MC", Dane.nowyKlient(), 100_000);
        assertThat(Dane.autoryzuj(sklep, karta.numer(), Dane.unikalny("rozgrzewka"), 1).status()).isEqualTo(200);
        String czas = LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("MMddHHmmss"));
        String stan = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));

        ISOMsg odpowiedz = zapytaj(komunikat("0100", karta.numer(), 15_000, sklep.id(), stan, czas));
        ISOMsg powtorka = zapytaj(komunikat("0100", karta.numer(), 15_000, sklep.id(), stan, czas));

        assertThat(odpowiedz.getMTI()).isEqualTo("0110");
        assertThat(odpowiedz.getString(39)).isEqualTo("00");
        assertThat(odpowiedz.getString(37)).matches("\\d{12}");
        assertThat(odpowiedz.getString(38)).hasSize(6);
        assertThat(odpowiedz.hasField(2)).isFalse();
        assertThat(powtorka.getString(37)).isEqualTo(odpowiedz.getString(37));
        assertThat(Dane.dostepneSrodki(karta)).isEqualTo(100_000 - 1 - 15_000);

        ISOMsg wycofanie = komunikat("0400", null, 15_000, sklep.id(), String.format("%06d", (Integer.parseInt(stan) + 1) % 1_000_000), czas);
        wycofanie.set(37, odpowiedz.getString(37));
        ISOMsg odpowiedzWycofania = zapytaj(wycofanie);

        assertThat(odpowiedzWycofania.getMTI()).isEqualTo("0410");
        assertThat(odpowiedzWycofania.getString(39)).isEqualTo("00");
        assertThat(Dane.dostepneSrodki(karta)).isEqualTo(100_000 - 1);

        ISOMsg zlyAkceptant = zapytaj(komunikat("0100", karta.numer(), 100, 999_999_999L, "000001", czas));
        assertThat(zlyAkceptant.getString(39)).isEqualTo("03");
    }
}
