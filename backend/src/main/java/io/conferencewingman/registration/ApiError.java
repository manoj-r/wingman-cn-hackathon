package io.conferencewingman.registration;

class ApiError extends RuntimeException {
    final int status;
    final String code;
    ApiError(int status,String code,String message) { super(message); this.status=status; this.code=code; }
}
