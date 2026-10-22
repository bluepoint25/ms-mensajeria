package com.tiendanative.mensajeria.common;

// Falla que puede resolverse sola (servicio externo caído, timeout): vale la pena reintentar
public class ErrorTransitorio extends RuntimeException {
    public ErrorTransitorio(String mensaje) {
        super(mensaje);
    }

    public ErrorTransitorio(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
