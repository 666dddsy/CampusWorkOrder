package com.example.campusworkorder.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(OutputCaptureExtension.class)
public class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler=new GlobalExceptionHandler();
    @Test
    void businessException_shouldKeepResponseAndWriteLog(CapturedOutput output){
        BusinessException ex= new BusinessException(HttpStatus.FORBIDDEN, "You are not ADMIN");
        ResponseEntity<Map<String,String>> response=handler.handleBusinessException(ex);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());

        assertNotNull(response.getBody());
        assertEquals(ex.getMessage(),response.getBody().get("message"));

        assertTrue(output.getAll().contains("business_rejected"));
        assertTrue(output.getAll().contains("status=403"));
    }
}
