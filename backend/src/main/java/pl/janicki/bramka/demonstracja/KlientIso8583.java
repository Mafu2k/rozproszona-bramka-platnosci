package pl.janicki.bramka.demonstracja;

import java.io.IOException;
import java.net.SocketException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.channel.ASCIIChannel;
import org.jpos.iso.packager.ISO87APackager;

final class KlientIso8583 implements AutoCloseable {

    record Odpowiedz(String kod, String rrn) {
    }

    private static final DateTimeFormatter CZAS_TRANSMISJI = DateTimeFormatter.ofPattern("MMddHHmmss");
    private static final String TERMINAL = "GEN00001";
    private static final String PLN = "985";
    private static final int CZAS_ODPOWIEDZI_MS = 15_000;

    private final BlockingQueue<ASCIIChannel> kanaly;
    private final AtomicInteger numerSystemowy = new AtomicInteger(ThreadLocalRandom.current().nextInt(1_000_000));

    KlientIso8583(String host, int port, int liczbaPolaczen) throws SocketException {
        kanaly = new ArrayBlockingQueue<>(liczbaPolaczen);
        for (int i = 0; i < liczbaPolaczen; i++) {
            ASCIIChannel kanal = new ASCIIChannel(host, port, new ISO87APackager());
            kanal.setTimeout(CZAS_ODPOWIEDZI_MS);
            kanaly.add(kanal);
        }
    }

    Odpowiedz autoryzuj(long idSklepu, String numerKarty, long kwota) throws IOException, ISOException {
        ISOMsg komunikat = komunikat("0100", idSklepu, kwota);
        komunikat.set(2, numerKarty);
        return wyslij(komunikat);
    }

    Odpowiedz wycofaj(long idSklepu, String rrnPierwotnej, long kwota) throws IOException, ISOException {
        ISOMsg komunikat = komunikat("0400", idSklepu, kwota);
        komunikat.set(37, rrnPierwotnej);
        return wyslij(komunikat);
    }

    private ISOMsg komunikat(String mti, long idSklepu, long kwota) throws ISOException {
        ISOMsg komunikat = new ISOMsg(mti);
        komunikat.set(3, "000000");
        komunikat.set(4, String.format("%012d", kwota));
        komunikat.set(7, LocalDateTime.now(ZoneOffset.UTC).format(CZAS_TRANSMISJI));
        komunikat.set(11, String.format("%06d", numerSystemowy.updateAndGet(n -> (n + 1) % 1_000_000)));
        komunikat.set(41, TERMINAL);
        komunikat.set(42, String.format("%015d", idSklepu));
        komunikat.set(49, PLN);
        return komunikat;
    }

    private Odpowiedz wyslij(ISOMsg komunikat) throws IOException, ISOException {
        ASCIIChannel kanal = pobierzKanal();
        try {
            if (!kanal.isConnected()) {
                kanal.connect();
            }
            kanal.send(komunikat);
            ISOMsg odpowiedz = kanal.receive();
            return new Odpowiedz(odpowiedz.getString(39), odpowiedz.getString(37));
        } catch (IOException | ISOException e) {
            kanal.disconnect();
            throw e;
        } finally {
            kanaly.add(kanal);
        }
    }

    private ASCIIChannel pobierzKanal() {
        try {
            return kanaly.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() throws IOException {
        for (ASCIIChannel kanal : kanaly) {
            kanal.disconnect();
        }
    }
}
