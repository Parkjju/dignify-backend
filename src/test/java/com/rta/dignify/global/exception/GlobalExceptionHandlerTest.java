package com.rta.dignify.global.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@WebMvcTest(controllers = TestController.class)
@AutoConfigureMockMvc(addFilters = false)
public class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("BusinessException -> 404")
    void businessExceptionTest() throws Exception {
        RequestBuilder requestBuilder = MockMvcRequestBuilders.get("/test/business-exception");
        mockMvc.perform(requestBuilder)
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("DataIntegrityViolationException -> 409")
    void dataIntegrityViolationTest() throws Exception {
        RequestBuilder requestBuilder = MockMvcRequestBuilders.get("/test/data-integrity");
        mockMvc.perform(requestBuilder)
                .andExpect(MockMvcResultMatchers.status().isConflict())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("DATA_INTEGRITY_VIOLATION"));
    }

    @Test
    @DisplayName("MethodArgumentNotValidException -> 400")
    void methodArgumentNotValidTest() throws Exception {
        RequestBuilder requestBuilder = MockMvcRequestBuilders.post("/test/method-argument-not-valid")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}");
        mockMvc.perform(requestBuilder)
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("METHOD_ARGUMENT_NOT_VALID"));
    }

    @Test
    @DisplayName("HttpMessageNotReadableException -> 400")
    void httpMessageNotReadableTest() throws Exception {
        RequestBuilder requestBuilder = MockMvcRequestBuilders.get("/test/http-message-not-readable");
        mockMvc.perform(requestBuilder)
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("METHOD_ARGUMENT_NOT_VALID"));
    }

    @Test
    @DisplayName("처리되지 않은 일반 Exception -> 500")
    void internalServerErrorTest() throws Exception {
        RequestBuilder requestBuilder = MockMvcRequestBuilders.post("/test/internal-server-error");
        mockMvc.perform(requestBuilder)
                .andExpect(MockMvcResultMatchers.status().isInternalServerError())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }

}
