package com.rta.dignify.dto.artistrequest;

import com.rta.dignify.domain.ArtistRequest;
import com.rta.dignify.domain.RequestStatus;

import java.time.Instant;

public record ArtistRequestResponse(Long id, String artistName, RequestStatus status,
                                    String cancelReason, Instant createdAt) {
    public static ArtistRequestResponse from(ArtistRequest r) {
        return new ArtistRequestResponse(r.getId(), r.getArtistName(), r.getStatus(),
                r.getCancelReason(), r.getCreatedAt());
    }
}
