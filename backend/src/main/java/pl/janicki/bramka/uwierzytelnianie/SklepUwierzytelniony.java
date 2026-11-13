package pl.janicki.bramka.uwierzytelnianie;

public record SklepUwierzytelniony(long id, String nazwa, String status) {

    public boolean aktywny() {
        return "AKTYWNY".equals(status);
    }
}
