package com.rta.dignify.controller;

import com.rta.dignify.dto.hype.HypeListResponse;
import com.rta.dignify.service.HypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/users")
@RestController
public class UserController {
    private final HypeService hypeService;

    @GetMapping("/me/hypes")
    public HypeListResponse getMyHypedTracks(@AuthenticationPrincipal Long userId, @RequestParam(required = false) Long cursor) {
        return hypeService.getMyHypedTracks(userId, cursor);
    }
}
