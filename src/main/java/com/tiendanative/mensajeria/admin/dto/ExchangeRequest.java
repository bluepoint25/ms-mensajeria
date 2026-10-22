package com.tiendanative.mensajeria.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ExchangeRequest(
    @NotBlank(message = "El nombre del exchange es obligatorio")
    @Pattern(regexp = NombreRecurso.PATRON, message = NombreRecurso.MENSAJE)
    String nombre,

    @NotNull(message = "El tipo es obligatorio: DIRECT, TOPIC o FANOUT")
    TipoExchange tipo,

    boolean durable
) {
    public enum TipoExchange { DIRECT, TOPIC, FANOUT }
}
