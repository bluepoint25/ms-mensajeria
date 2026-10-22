package com.tiendanative.mensajeria.admin.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record QueueRequest(
    @NotBlank(message = "El nombre de la cola es obligatorio")
    @Pattern(regexp = NombreRecurso.PATRON, message = NombreRecurso.MENSAJE)
    String nombre,

    @NotNull(message = "El tipo es obligatorio: CLASSIC o QUORUM")
    TipoCola tipo,

    boolean durable,

    // true: crea también <nombre>.dlq y deja la cola apuntando a ella por el DLX
    boolean conDlq
) {

    public enum TipoCola { CLASSIC, QUORUM }

    @AssertTrue(message = "Una cola QUORUM siempre es durable")
    public boolean isConfiguracionValida() {
        return tipo != TipoCola.QUORUM || durable;
    }
}
