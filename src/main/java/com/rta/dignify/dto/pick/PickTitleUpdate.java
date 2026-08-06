package com.rta.dignify.dto.pick;

import jakarta.validation.constraints.Size;

public record PickTitleUpdate(@Size(max = 120) String title) {
}
