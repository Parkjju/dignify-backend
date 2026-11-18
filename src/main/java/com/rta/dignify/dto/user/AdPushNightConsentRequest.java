package com.rta.dignify.dto.user;

import jakarta.validation.constraints.NotNull;

public record AdPushNightConsentRequest(@NotNull Boolean nightPush) {
}
