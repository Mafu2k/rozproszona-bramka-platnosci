package pl.janicki.bramka.iso8583;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.ISO87APackager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import pl.janicki.bramka.bledy.BladApi;
import pl.janicki.bramka.platnosci.PlatnosciService;
import pl.janicki.bramka.platnosci.WynikPlatnosci;
import pl.janicki.bramka.platnosci.ZadanieAutoryzacji;
import pl.janicki.bramka.platnosci.ZadanieZwrotu;
import pl.janicki.bramka.uwierzytelnianie.RejestrSklepow;
import pl.janicki.bramka.uwierzytelnianie.SklepUwierzytelniony;

class ObslugaIso8583Test {

    private PlatnosciService platnosci;
    private ObslugaIso8583 obsluga;

    @BeforeEach
    void przygotuj() {
        platnosci = mock(PlatnosciService.class);
        RejestrSklepow rejestr = mock(RejestrSklepow.class);
        when(rejestr.poIdentyfikatorze(7L)).thenReturn(Optional.of(new SklepUwierzytelniony(7, "Sklep", "AKTYWNY")));
        when(rejestr.poIdentyfikatorze(8L)).thenReturn(Optional.of(new SklepUwierzytelniony(8, "Zawieszony", "ZAWIESZONY")));
        obsluga = new ObslugaIso8583(platnosci, rejestr, new SimpleMeterRegistry());
    }

    private static ISOMsg komunikat(String mti, String sklep) throws ISOException {
        ISOMsg m = new ISOMsg(mti);
        m.setPackager(new ISO87APackager());
        m.set(2, "5555555555554444");
        m.set(3, "000000");
        m.set(4, "000000012999");
        m.set(7, "1113123000");
        m.set(11, "000123");
        m.set(41, "TERM0001");
        m.set(42, sklep);
        m.set(49, "985");
        return m;
    }

    private static WynikPlatnosci wynik(String kod, String rrn) {
        return new WynikPlatnosci("x", WynikPlatnosci.decyzja(kod), kod, "", rrn, "AUTORYZACJA", 12999, "PLN", "MC",
                "555555******4444", "AUTORYZOWANA", OffsetDateTime.now(), null, false);
    }

    private ISOMsg obsluz(ISOMsg zadanie) throws ISOException {
        ISOMsg odpowiedz = (ISOMsg) zadanie.clone();
        odpowiedz.setResponseMTI();
        odpowiedz.unset(2);
        obsluga.obsluz(zadanie, odpowiedz);
        odpowiedz.pack();
        return odpowiedz;
    }

    @Test
    void autoryzacja0100MapujeIdentyfikatorZadaniaIKodOdpowiedzi() throws ISOException {
        when(platnosci.autoryzuj(eq(7L), any())).thenReturn(wynik("00", "631720000042"));
        ISOMsg odpowiedz = obsluz(komunikat("0100", "000000000000007"));

        ArgumentCaptor<ZadanieAutoryzacji> zadanie = ArgumentCaptor.forClass(ZadanieAutoryzacji.class);
        verify(platnosci).autoryzuj(eq(7L), zadanie.capture());
        assertThat(zadanie.getValue().idZadania()).isEqualTo("ISO-000000000000007-000123-1113123000");
        assertThat(zadanie.getValue().kwota()).isEqualTo(12999L);
        assertThat(zadanie.getValue().waluta()).isEqualTo("PLN");
        assertThat(odpowiedz.getMTI()).isEqualTo("0110");
        assertThat(odpowiedz.getString(39)).isEqualTo("00");
        assertThat(odpowiedz.getString(37)).isEqualTo("631720000042");
        assertThat(odpowiedz.getString(38)).isEqualTo("000042");
        assertThat(odpowiedz.hasField(2)).isFalse();
    }

    @Test
    void wycofanie0400WywolujeZwrotPoRrn() throws ISOException {
        when(platnosci.zwroc(eq(7L), any())).thenReturn(wynik("00", "631720000043"));
        ISOMsg zadanie = komunikat("0400", "000000000000007");
        zadanie.set(37, "631720000042");
        ISOMsg odpowiedz = obsluz(zadanie);

        ArgumentCaptor<ZadanieZwrotu> zwrot = ArgumentCaptor.forClass(ZadanieZwrotu.class);
        verify(platnosci).zwroc(eq(7L), zwrot.capture());
        assertThat(zwrot.getValue().rrnPierwotnej()).isEqualTo("631720000042");
        assertThat(odpowiedz.getMTI()).isEqualTo("0410");
        assertThat(odpowiedz.getString(39)).isEqualTo("00");
    }

    @Test
    void nieaktywnyAlboNieznanySklepDostajeKod03() throws ISOException {
        assertThat(obsluz(komunikat("0100", "000000000000008")).getString(39)).isEqualTo("03");
        assertThat(obsluz(komunikat("0100", "000000000000099")).getString(39)).isEqualTo("03");
    }

    @Test
    void bladBramkiMapowanyNaKodIso() throws ISOException {
        when(platnosci.autoryzuj(eq(7L), any())).thenThrow(BladApi.siecNiedostepna("MC"));
        assertThat(obsluz(komunikat("0100", "000000000000007")).getString(39)).isEqualTo("91");
    }

    @Test
    void nieznanaWalutaDostajeKod13() throws ISOException {
        ISOMsg zadanie = komunikat("0100", "000000000000007");
        zadanie.set(49, "999");
        assertThat(obsluz(zadanie).getString(39)).isEqualTo("13");
    }
}
