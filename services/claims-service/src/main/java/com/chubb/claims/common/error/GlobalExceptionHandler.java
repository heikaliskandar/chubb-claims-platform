package com.chubb.claims.common.error;

import com.chubb.claims.claim.application.ClaimNotFoundException;
import com.chubb.claims.claim.domain.ClaimAccessDeniedException;
import com.chubb.claims.claim.domain.ClaimRuleViolationException;
import com.chubb.claims.claim.domain.InformationRequestNotFoundException;
import com.chubb.claims.claim.domain.InvalidClaimStateException;
import com.chubb.claims.employee.application.EmployeeNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler({ClaimNotFoundException.class, InformationRequestNotFoundException.class,
            EmployeeNotFoundException.class})
    ProblemDetail handleNotFound(RuntimeException exception) {
        return problem(HttpStatus.NOT_FOUND, "Not found", exception.getMessage());
    }

    @ExceptionHandler(InvalidClaimStateException.class)
    ProblemDetail handleInvalidState(InvalidClaimStateException exception) {
        return problem(HttpStatus.CONFLICT, "Invalid claim state", exception.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleConcurrentUpdate() {
        return problem(HttpStatus.CONFLICT, "Concurrent update",
                "The claim was changed by someone else. Reload it and try again.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrityViolation() {
        return problem(HttpStatus.CONFLICT, "Conflict",
                "The request conflicts with existing data, for example a duplicate request still being processed.");
    }

    @ExceptionHandler(ClaimRuleViolationException.class)
    ProblemDetail handleRuleViolation(ClaimRuleViolationException exception) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Business rule violated", exception.getMessage());
    }

    @ExceptionHandler(ClaimAccessDeniedException.class)
    ProblemDetail handleClaimAccessDenied(ClaimAccessDeniedException exception) {
        return problem(HttpStatus.FORBIDDEN, "Forbidden", exception.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied() {
        return problem(HttpStatus.FORBIDDEN, "Forbidden", "You do not have permission to perform this action.");
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleUnauthenticated() {
        return problem(HttpStatus.UNAUTHORIZED, "Unauthorized", "Valid credentials are required.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getFieldErrors().forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        return validationProblem(exception, errors, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (ParameterValidationResult result : exception.getParameterValidationResults()) {
            if (result instanceof ParameterErrors parameterErrors) {
                for (FieldError error : parameterErrors.getFieldErrors()) {
                    errors.put(error.getField(), error.getDefaultMessage());
                }
            } else {
                String parameter = result.getMethodParameter().getParameterName();
                result.getResolvableErrors().forEach(error -> errors.put(parameter, error.getDefaultMessage()));
            }
        }
        return validationProblem(exception, errors, headers, status, request);
    }

    private ResponseEntity<Object> validationProblem(Exception exception, Map<String, String> errors,
                                                     HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Validation failed", "One or more fields are invalid.");
        body.setProperty("errors", errors);
        return handleExceptionInternal(exception, body, headers, status, request);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
