package pl.janicki.bramka.demonstracja;

import java.util.Map;

record Konfiguracja(String adresBramki, String hostIso, int portIso, String hasloAdmina, String hasloOperatorow, double tps,
        double udzialIso) {

    static Konfiguracja zeSrodowiska(Map<String, String> srodowisko) {
        String[] adresIso = srodowisko.getOrDefault("ISO8583_ADRES", "localhost:8583").split(":");
        double tps = Double.parseDouble(srodowisko.getOrDefault("RUCH_TPS", "20"));
        double udzialIso = Double.parseDouble(srodowisko.getOrDefault("RUCH_UDZIAL_ISO", "0.2"));
        if (tps <= 0 || tps > 500) {
            throw new IllegalArgumentException("RUCH_TPS musi być z przedziału (0, 500]");
        }
        if (udzialIso < 0 || udzialIso > 1) {
            throw new IllegalArgumentException("RUCH_UDZIAL_ISO musi być z przedziału [0, 1]");
        }
        return new Konfiguracja(
                srodowisko.getOrDefault("BRAMKA_URL", "http://localhost:8080"),
                adresIso[0],
                Integer.parseInt(adresIso[1]),
                wymagana(srodowisko, "PANEL_ADMIN_PASSWORD"),
                wymagana(srodowisko, "PANEL_OPERATOR_PASSWORD"),
                tps,
                udzialIso);
    }

    private static String wymagana(Map<String, String> srodowisko, String nazwa) {
        String wartosc = srodowisko.get(nazwa);
        if (wartosc == null || wartosc.isBlank()) {
            throw new IllegalStateException("Brak zmiennej " + nazwa);
        }
        return wartosc;
    }
}
