package com.rta.dignify.controller;

import com.rta.dignify.dto.artistrequest.ArtistRequestCreate;
import com.rta.dignify.dto.artistrequest.ArtistRequestListResponse;
import com.rta.dignify.dto.artistrequest.ArtistRequestResponse;
import com.rta.dignify.service.ArtistRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RequestMapping("/artist-requests")
@RestController
public class ArtistRequestController {
    private final ArtistRequestService service;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ArtistRequestResponse create(@AuthenticationPrincipal Long userId,
                                        @RequestBody @Valid ArtistRequestCreate request) {
        return service.create(userId, request.artistName());
    }

    @GetMapping
    public ArtistRequestListResponse history(@AuthenticationPrincipal Long userId) {
        return new ArtistRequestListResponse(service.history(userId));
    }
}
