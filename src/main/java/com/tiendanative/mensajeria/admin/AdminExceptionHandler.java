package com.tiendanative.mensajeria.admin;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class AdminExceptionHandler {

    public record ErrorResponse(int status, String mensaje, Map<String, String> errores, Instant fecha) {
        static ErrorResponse de(HttpStatus status, String mensaje) {
            return new ErrorResponse(status.value(), mensaje, Map.of(), Instant.now());
        }
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validacion(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> errores.put(e.getField(), e.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors().forEach(e -> errores.put(e.getObjectName(), e.getDefaultMessage()));
        return ResponseEntity.badRequest()
            .body(new ErrorResponse(400, "Parámetros inválidos", errores, Instant.now()));
    }

    // Enums con un valor desconocido (tipo: "OTRO") o JSON mal formado
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> cuerpoIlegible(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(ErrorResponse.de(HttpStatus.BAD_REQUEST,
            "Cuerpo inválido: revise el JSON y los valores permitidos para 'tipo'"));
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> noEncontrado(RecursoNoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.de(HttpStatus.NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler({RecursoExistenteException.class, RecursoProtegidoException.class})
    public ResponseEntity<ErrorResponse> conflicto(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.de(HttpStatus.CONFLICT, ex.getMessage()));
    }

    @ExceptionHandler(AmqpException.class)
    public ResponseEntity<ErrorResponse> broker(AmqpException ex) {
        log.error("Error de RabbitMQ", ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ErrorResponse.de(HttpStatus.SERVICE_UNAVAILABLE, "RabbitMQ rechazó la operación: " + ex.getMessage()));
    }
}
