package com.rta.dignify.dto.report;

import com.rta.dignify.domain.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReportCreate(@NotNull Long pickId, @NotNull ReportReason reason, @Size(max = 200) String detail) {
}
