package com.rta.dignify.dto.pick;

import jakarta.validation.constraints.NotBlank;

public record PickReactionRequest(@NotBlank String emoji) {
}
