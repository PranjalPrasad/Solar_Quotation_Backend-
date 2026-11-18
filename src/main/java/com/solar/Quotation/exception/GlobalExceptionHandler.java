package com.solar.Quotation.exception;

import com.solar.Quotation.dto.responsedto.ApiResponse;
import com.solar.Quotation.logging.RequestLoggingFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Every exception that reaches a controller is logged exactly once, here, in respond():
 *   4xx -> WARN, one line, no stack trace      5xx -> ERROR, with stack trace
 * The client only gets a safe message, the error code and the requestId; never a stack trace or exception text
 * from the framework. Services must NOT log an exception they rethrow (that would log it twice).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> notFound(ResourceNotFoundException ex, HttpServletRequest req) {
        return respond(req, HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), ex.getMessage(), null, ex);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> business(BusinessException ex, HttpServletRequest req) {
        return respond(req, ex.getStatus(), ex.getErrorCode(), ex.getMessage(), ex.getMessage(), null, ex);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> api(ApiException ex, HttpServletRequest req) {
        HttpStatus st = HttpStatus.resolve(ex.getStatus());
        if (st == null) st = HttpStatus.INTERNAL_SERVER_ERROR;
        String clientMsg = st.is5xxServerError() ? "Something went wrong" : ex.getMessage();
        return respond(req, st, "API_ERROR", clientMsg, ex.getMessage(), null, ex);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> forbidden(ForbiddenException ex, HttpServletRequest req) {
        return respond(req, HttpStatus.FORBIDDEN, ex.getErrorCode(), ex.getMessage(), ex.getMessage(), null, ex);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> accessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return respond(req, HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied", "Access denied", null, ex);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<ApiResponse.FieldError> errs = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> new ApiResponse.FieldError(f.getField(), f.getDefaultMessage())).toList();
        // only field NAMES are logged, never the rejected values (they may be personal data)
        List<String> fields = ex.getBindingResult().getFieldErrors().stream().map(f -> f.getField()).distinct().toList();
        return respond(req, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Validation failed",
                "Validation failed, fields=" + fields, errs, ex);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> unreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        // the exception text can quote part of the request body, so it is not logged
        return respond(req, HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Malformed request body",
                "Malformed request body (" + ex.getClass().getSimpleName() + ")", null, ex);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> badParam(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return respond(req, HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "Invalid value for '" + ex.getName() + "'",
                "Invalid parameter " + ex.getName(), null, ex);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> missingParam(MissingServletRequestParameterException ex, HttpServletRequest req) {
        return respond(req, HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", "Missing parameter '" + ex.getParameterName() + "'",
                "Missing parameter " + ex.getParameterName(), null, ex);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> methodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return respond(req, HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Method not allowed",
                "Method not allowed: " + ex.getMethod(), null, ex);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> noResource(NoResourceFoundException ex, HttpServletRequest req) {
        return respond(req, HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found", "No such endpoint", null, ex);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> tooLarge(MaxUploadSizeExceededException ex, HttpServletRequest req) {
        // Rejected by the servlet container's multipart limit before FileController runs, so the [FILE] line is written here.
        log.warn("[FILE] upload rejected purpose=- reason=TOO_LARGE_MULTIPART userId={} partnerId={}",
                MDC.get(RequestLoggingFilter.MDC_USER_ID), MDC.get(RequestLoggingFilter.MDC_PARTNER_ID));
        return respond(req, HttpStatus.CONTENT_TOO_LARGE, "FILE_TOO_LARGE", "File is too large", "Upload too large", null, ex);
    }

    /** Not a readable multipart body (broken boundary, aborted upload). The exception text is not echoed. */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> badMultipart(MultipartException ex, HttpServletRequest req) {
        return respond(req, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Could not read the uploaded file",
                "Unreadable multipart request (" + ex.getClass().getSimpleName() + ")", null, ex);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> unsupportedMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest req) {
        return respond(req, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Unsupported content type",
                "Unsupported content type", null, ex);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> versionConflict(OptimisticLockingFailureException ex, HttpServletRequest req) {
        return respond(req, HttpStatus.CONFLICT, "VERSION_CONFLICT",
                "Record was changed by someone else. Reload and try again.", "Optimistic lock conflict", null, ex);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> duplicate(DataIntegrityViolationException ex, HttpServletRequest req) {
        // the DB message contains the duplicate value (often an email / phone), so only the cause type is logged
        String cause = ex.getMostSpecificCause().getClass().getSimpleName();
        return respond(req, HttpStatus.CONFLICT, "DUPLICATE", "Duplicate or invalid data",
                "Data integrity violation (" + cause + ")", null, ex);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> generic(Exception ex, HttpServletRequest req) {
        return respond(req, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Something went wrong",
                "Unhandled " + ex.getClass().getSimpleName(), null, ex);
    }

    /** The only place an exception is logged and turned into a response. */
    private ResponseEntity<ApiResponse<Void>> respond(HttpServletRequest req, HttpStatus st, String code,
                                                      String clientMsg, String logMsg,
                                                      List<ApiResponse.FieldError> errs, Throwable ex) {
        if (st.is5xxServerError()) {
            log.error("[API] {} {} -> {} {}: {}", req.getMethod(), req.getRequestURI(), st.value(), code, logMsg, ex);
        } else {
            log.warn("[API] {} {} -> {} {}: {}", req.getMethod(), req.getRequestURI(), st.value(), code, logMsg);
        }
        ApiResponse<Void> body = ApiResponse.error(clientMsg, code, errs);
        body.setRequestId(MDC.get(RequestLoggingFilter.MDC_REQUEST_ID));
        return ResponseEntity.status(st).body(body);
    }
}
