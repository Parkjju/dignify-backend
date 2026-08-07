package com.rta.dignify.controller;

import com.rta.dignify.dto.feed.FeedResponse;
import com.rta.dignify.dto.pick.PickCreate;
import com.rta.dignify.dto.pick.PickListResponse;
import com.rta.dignify.dto.pick.PickResponse;
import com.rta.dignify.global.exception.BusinessException;
import com.rta.dignify.global.exception.ErrorCode;
import com.rta.dignify.service.PickService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RequestMapping("/picks")
@RestController
public class PickController {
    private final PickService pickService;

    @GetMapping
    public PickListResponse getPickList(@AuthenticationPrincipal Long userId, @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "false") boolean mine) {
        if (mine && userId == null) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        }
        return pickService.getPicks(userId, cursor, mine);
    }

    @GetMapping("/{pickId}")
    public FeedResponse getPickDetail(@AuthenticationPrincipal Long userId, @PathVariable Long pickId) {
        return pickService.getPickDetail(userId, pickId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void create(@AuthenticationPrincipal Long userId, @RequestBody @Valid PickCreate request) {
        pickService.createPick(userId, request);
    }

    @DeleteMapping("/{pickId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePick(@AuthenticationPrincipal Long userId, @PathVariable Long pickId) {
        pickService.deletePick(userId, pickId);
    }
}
