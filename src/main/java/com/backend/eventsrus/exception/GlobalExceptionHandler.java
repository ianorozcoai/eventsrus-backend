package com.backend.eventsrus.exception;

import com.backend.eventsrus.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fieldError -> fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage()));

        return build(HttpStatus.BAD_REQUEST, "Validation failed", request, fieldErrors);
    }

    @ExceptionHandler(DuplicateUserException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateUser(
            DuplicateUserException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request, null);
    }

    @ExceptionHandler(AccountIdentityConflictException.class)
    public ResponseEntity<ErrorResponse> handleAccountIdentityConflict(
            AccountIdentityConflictException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request, null);
    }

    @ExceptionHandler(InvalidCurrentPasswordException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCurrentPassword(
            InvalidCurrentPasswordException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request, null);
    }

    @ExceptionHandler(DuplicateAdminUsernameException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateAdminUsername(
            DuplicateAdminUsernameException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request, null);
    }

    @ExceptionHandler(InvalidGoogleTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidGoogleToken(
            InvalidGoogleTokenException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request, null);
    }

    @ExceptionHandler(SubscriptionConflictException.class)
    public ResponseEntity<ErrorResponse> handleSubscriptionConflict(
            SubscriptionConflictException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request, null);
    }

    @ExceptionHandler(QuotationConflictException.class)
    public ResponseEntity<ErrorResponse> handleQuotationConflict(
            QuotationConflictException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request, null);
    }

    @ExceptionHandler(SubscriptionExpiredException.class)
    public ResponseEntity<ErrorResponse> handleSubscriptionExpired(
            SubscriptionExpiredException ex, HttpServletRequest request) {
        return build(HttpStatus.PAYMENT_REQUIRED, ex.getMessage(), request, null);
    }

    @ExceptionHandler(RecaptchaVerificationException.class)
    public ResponseEntity<ErrorResponse> handleRecaptchaVerification(
            RecaptchaVerificationException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request, null);
    }

    @ExceptionHandler(InvalidFileTypeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidFileType(
            InvalidFileTypeException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request, null);
    }

    @ExceptionHandler(InvalidRejectionReasonException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRejectionReason(
            InvalidRejectionReasonException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request, null);
    }

    @ExceptionHandler(InvalidReferralCodeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidReferralCode(
            InvalidReferralCodeException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request, null);
    }

    @ExceptionHandler(InvalidPromoCodeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPromoCode(
            InvalidPromoCodeException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request, null);
    }

    @ExceptionHandler(PayPalApiException.class)
    public ResponseEntity<ErrorResponse> handlePayPalApiException(
            PayPalApiException ex, HttpServletRequest request) {
        log.error("PayPal API error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.BAD_GATEWAY, ex.getMessage(), request, null);
    }

    @ExceptionHandler(AnthropicApiException.class)
    public ResponseEntity<ErrorResponse> handleAnthropicApiException(
            AnthropicApiException ex, HttpServletRequest request) {
        log.error("Anthropic API error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.BAD_GATEWAY, ex.getMessage(), request, null);
    }

    @ExceptionHandler(CoordinatorQuotaExceededException.class)
    public ResponseEntity<ErrorResponse> handleCoordinatorQuotaExceeded(
            CoordinatorQuotaExceededException ex, HttpServletRequest request) {
        return build(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request, null);
    }

    @ExceptionHandler(ReviewNotAllowedException.class)
    public ResponseEntity<ErrorResponse> handleReviewNotAllowed(
            ReviewNotAllowedException ex, HttpServletRequest request) {
        return build(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage(), request, null);
    }

    @ExceptionHandler(InvalidSystemSettingException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSystemSetting(
            InvalidSystemSettingException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request, null);
    }

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status, String message, HttpServletRequest request, Map<String, String> fieldErrors) {
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI())
                .fieldErrors(fieldErrors)
                .build();
        return ResponseEntity.status(status).body(body);
    }
}
