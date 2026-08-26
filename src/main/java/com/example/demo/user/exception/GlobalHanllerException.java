package com.example.demo.user.exception;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.example.demo.user.dto_request.ApiResponse;
@ControllerAdvice
public class GlobalHanllerException {
    @ExceptionHandler(value= Exception.class)
    public ResponseEntity<ApiResponse> handleRuntimeException(Exception ex) {
        ApiResponse<String> response = new ApiResponse<>();
        response.setCode(ErrorCode.UNCAUGHT_EXCEPTION.getCode());
        response.setMessage(ErrorCode.UNCAUGHT_EXCEPTION.getMessage());
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(value= AppException.class)
    public ResponseEntity<ApiResponse> handleAppException(AppException ex) {
        ErrorCode errorCode = (ErrorCode)ex.getErrorCode();
        ApiResponse response = new ApiResponse<>();
        response.setCode(errorCode.getCode());
        response.setMessage(errorCode.getMessage());
        return ResponseEntity.badRequest().body(response);
    }
    @ExceptionHandler(value= MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        String enumKey=ex.getFieldError().getDefaultMessage();
        ErrorCode errorCode=ErrorCode.INVALID_KEY;
        try{
            errorCode=ErrorCode.valueOf(enumKey);}
        catch (IllegalArgumentException e) {
            
        }
        ApiResponse response = new ApiResponse<>();
        response.setCode(errorCode.getCode());
        response.setMessage(errorCode.getMessage());
        return ResponseEntity.badRequest().body(response);
    }   
}
