package pl.janicki.bramka.platnosci;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pl.janicki.bramka.numer.NumerKarty;

public record ZadanieAutoryzacji(
        @NotBlank @Size(max = 64) @Pattern(regexp = WzorceDanych.ID_ZADANIA) String idZadania,
        @NotBlank String numerKarty,
        @NotNull @Min(1) @Max(WzorceDanych.MAKS_KWOTA) Long kwota,
        @NotBlank @Pattern(regexp = WzorceDanych.WALUTA) String waluta) {

    @Override
    public String toString() {
        String maska = numerKarty != null && numerKarty.length() >= NumerKarty.MIN_DLUGOSC
                ? NumerKarty.maska(numerKarty) : "***";
        return "ZadanieAutoryzacji[idZadania=" + idZadania + ", numerKarty=" + maska + ", kwota=" + kwota + ", waluta=" + waluta + "]";
    }
}
