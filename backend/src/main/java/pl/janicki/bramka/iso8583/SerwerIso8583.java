package pl.janicki.bramka.iso8583;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.jpos.iso.ISOServer;
import org.jpos.iso.channel.ASCIIChannel;
import org.jpos.iso.packager.ISO87APackager;
import org.jpos.util.ThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import pl.janicki.bramka.konfiguracja.BramkaProperties;

@Component
public class SerwerIso8583 implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(SerwerIso8583.class);

    private final BramkaProperties.Iso8583 konfiguracja;
    private final ObslugaIso8583 obsluga;
    private ISOServer serwer;
    private volatile boolean dziala;

    public SerwerIso8583(BramkaProperties properties, ObslugaIso8583 obsluga) {
        this.konfiguracja = properties.iso8583();
        this.obsluga = obsluga;
    }

    @Override
    public void start() {
        if (konfiguracja == null || !konfiguracja.wlaczony()) {
            return;
        }
        try {
            serwer = new ISOServer(konfiguracja.port(), new ASCIIChannel(new ISO87APackager()), new ThreadPool(1, 100));
        } catch (IOException e) {
            throw new UncheckedIOException("Nie udało się utworzyć kanału ISO 8583", e);
        }
        serwer.addISORequestListener(obsluga);
        Thread watek = new Thread(serwer, "serwer-iso8583");
        watek.setDaemon(true);
        watek.start();
        dziala = true;
        log.info("Serwer ISO 8583 (jPOS, ASCII 1987) nasłuchuje na porcie {}", konfiguracja.port());
    }

    @Override
    public void stop() {
        if (serwer != null) {
            serwer.shutdown();
        }
        dziala = false;
    }

    @Override
    public boolean isRunning() {
        return dziala;
    }
}
