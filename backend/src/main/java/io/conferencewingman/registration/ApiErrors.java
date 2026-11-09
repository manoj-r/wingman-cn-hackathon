package io.conferencewingman.registration;

import java.util.*;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
class ApiErrors {
    record ErrorBody(String code,String message,Map<String,String> fields,String traceId) {}

    @ExceptionHandler(ApiError.class)
    ResponseEntity<?> known(ApiError error) { return response(error.status,error.code,error.getMessage(),Map.of()); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> validation(MethodArgumentNotValidException error) {
        Map<String,String> fields=new LinkedHashMap<>();
        error.getBindingResult().getFieldErrors().forEach(field -> fields.put(field.getField(),"Check this field."));
        return response(400,"VALIDATION_ERROR","Check the highlighted fields.",fields);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,MissingRequestHeaderException.class,MethodArgumentTypeMismatchException.class})
    ResponseEntity<?> malformed(Exception error) { return response(400,"VALIDATION_ERROR","Check the request fields and headers.",Map.of()); }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<?> database(DataAccessException error) { return response(503,"DATABASE_UNAVAILABLE","Please retry shortly.",Map.of()); }

    private ResponseEntity<?> response(int status,String code,String message,Map<String,String> fields) {
        var builder=ResponseEntity.status(status).cacheControl(CacheControl.noStore());
        if (status==503) builder.header(HttpHeaders.RETRY_AFTER,"5");
        return builder.body(Map.of("error",new ErrorBody(code,message,fields,UUID.randomUUID().toString())));
    }
}
