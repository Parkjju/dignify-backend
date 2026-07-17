package com.rta.dignify.dto;

import jakarta.validation.constraints.NotBlank;

public record DeviceTokenRegister(@NotBlank String token, @NotBlank String environment) {
}
