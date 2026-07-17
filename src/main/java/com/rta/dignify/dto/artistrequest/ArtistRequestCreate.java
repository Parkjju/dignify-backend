package com.rta.dignify.dto.artistrequest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ArtistRequestCreate(@NotBlank @Size(max = 100) String artistName) {
}
