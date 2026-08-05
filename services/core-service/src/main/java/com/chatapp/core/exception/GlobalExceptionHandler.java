package com.chatapp.core.exception;

import com.chatapp.core.exception.common.AppException;
import com.chatapp.core.exception.common.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.chatapp.core.base.ApiResponse;

/** Every error is returned via {@link ApiResponse#fail} — same envelope as a success response. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateUserException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateUser(DuplicateUserException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail("USER_ALREADY_EXISTS", ex.getMessage()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidCredentials(InvalidCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.fail("INVALID_CREDENTIALS", ex.getMessage()));
    }

    @ExceptionHandler(TwoFactorMethodNotEnabledException.class)
    public ResponseEntity<ApiResponse<Void>> handleTwoFactorMethodNotEnabled(TwoFactorMethodNotEnabledException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail("METHOD_NOT_ENABLED", ex.getMessage()));
    }

    @ExceptionHandler(TwoFactorMethodAlreadyEnabledException.class)
    public ResponseEntity<ApiResponse<Void>> handleTwoFactorMethodAlreadyEnabled(TwoFactorMethodAlreadyEnabledException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail("METHOD_ALREADY_ENABLED", ex.getMessage()));
    }

    @ExceptionHandler(OtpLockedException.class)
    public ResponseEntity<ApiResponse<Void>> handleOtpLocked(OtpLockedException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.fail("OTP_LOCKED", ex.getMessage()));
    }

    /** Every friend/#19-20 error (and any future domain that adopts this pattern instead of a
     *  dedicated exception class per case) — {@code errorCode.name()} becomes the String
     *  {@code ApiError.code}, same format as every handler above. */
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(AppException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        return ResponseEntity.status(errorCode.getHttpStatusCode())
                .body(ApiResponse.fail(errorCode.name(), errorCode.getMessage()));
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupported(UnsupportedOperationException ex) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(ApiResponse.fail("NOT_IMPLEMENTED", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().isEmpty()
                ? "Invalid request"
                : ex.getBindingResult().getFieldErrors().get(0).getField() + " " + ex.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail("VALIDATION_ERROR", message));
    }
}
