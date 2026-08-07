package com.rta.dignify.controller;

import com.rta.dignify.dto.report.ReportCreate;
import com.rta.dignify.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RequestMapping("/reports")
@RestController
public class ReportController {
    private final ReportService reportService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void create(@AuthenticationPrincipal Long userId, @RequestBody @Valid ReportCreate request) {
        reportService.create(userId, request);
    }
}
