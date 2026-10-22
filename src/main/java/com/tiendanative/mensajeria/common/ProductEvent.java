package com.tiendanative.mensajeria.common;

import java.time.Instant;

// Contrato del evento que publica ms-productos (mismos campos, JSON)
public record ProductEvent(String tipo, String productoId, String nombre, Integer stock, Instant fecha) {}
