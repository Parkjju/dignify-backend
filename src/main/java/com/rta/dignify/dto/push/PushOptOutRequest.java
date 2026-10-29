package com.rta.dignify.dto.push;

import jakarta.validation.constraints.NotBlank;

public record PushOptOutRequest(@NotBlank String deviceToken) {
}
