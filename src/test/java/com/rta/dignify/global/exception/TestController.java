package com.rta.dignify.global.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @GetMapping("/test/business-exception")
    void throwBusinessException() {
        throw new BusinessException(ErrorCode.USER_NOT_FOUND);
    }

    @GetMapping("/test/data-integrity")
    void throwDataIntegrity() {
        throw new DataIntegrityViolationException("test");
    }

    @PostMapping("/test/method-argument-not-valid")
    void throwMethodArgumentNotValid(@RequestBody @Valid TestRequest request) {
    }

    record TestRequest(@NotBlank String value) {
    }

    @PostMapping("/test/internal-server-error")
    void throwInternalServerError() throws Exception {
        throw new Exception("internal server error");
    }

    @GetMapping("/test/http-message-not-readable")
    void throwHttpMessageNotReadable() {
        throw new HttpMessageNotReadableException("error", new RuntimeException("test"));
    }
}
