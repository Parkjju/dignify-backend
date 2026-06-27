package com.rta.dignify.dto.user;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PreferGenreUpdateRequest(@NotNull @Size(max = 3) List<Long> genreIds) {
}
