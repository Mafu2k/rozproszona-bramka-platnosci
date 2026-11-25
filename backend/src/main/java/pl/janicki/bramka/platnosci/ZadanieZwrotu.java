package pl.janicki.bramka.platnosci;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ZadanieZwrotu(
        @NotBlank @Size(max = 64) @Pattern(regexp = WzorceDanych.ID_ZADANIA) String idZadania,
        @NotBlank @Pattern(regexp = WzorceDanych.RRN) String rrnPierwotnej,
        @NotNull @Min(1) @Max(WzorceDanych.MAKS_KWOTA) Long kwota,
        @NotBlank @Pattern(regexp = WzorceDanych.WALUTA) String waluta) {
}
