package com.tiendanative.mensajeria.common;

// Falla que se repetiría en cada intento (JSON inválido, datos imposibles): directo a la DLQ
public class ErrorPermanente extends RuntimeException {
    public ErrorPermanente(String mensaje) {
        super(mensaje);
    }

    public ErrorPermanente(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
