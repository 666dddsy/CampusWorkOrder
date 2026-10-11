package com.example.campusworkorder.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String,String>> handleValidException(MethodArgumentNotValidException ex){
        Map<String,String> errorMap=new HashMap<>();
        for(FieldError fieldError:ex.getBindingResult().getFieldErrors()){
            errorMap.put(fieldError.getField(),fieldError.getDefaultMessage());
        }
        return new ResponseEntity<>(errorMap, HttpStatus.BAD_REQUEST);
    }
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, String>> handleBusinessException(
            BusinessException ex
    ) {
        Map<String, String> errorMap = new HashMap<>();
        errorMap.put("message", ex.getMessage());

        return ResponseEntity
                .status(ex.getStatus())
                .body(errorMap);
    }
}
