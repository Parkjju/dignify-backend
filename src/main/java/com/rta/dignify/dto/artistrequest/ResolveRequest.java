package com.rta.dignify.dto.artistrequest;

import com.rta.dignify.domain.RequestStatus;

public record ResolveRequest(RequestStatus status, String cancelReason) {
}
