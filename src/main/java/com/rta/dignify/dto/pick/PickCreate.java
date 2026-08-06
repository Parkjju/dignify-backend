package com.rta.dignify.dto.pick;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PickCreate(@Size(max = 120) String title, @NotNull @Size(min = 1, max = 30) List<Long> trackIds) {
}
