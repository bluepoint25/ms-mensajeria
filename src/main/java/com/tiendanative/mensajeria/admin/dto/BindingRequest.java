package com.tiendanative.mensajeria.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BindingRequest(
    @NotBlank(message = "La cola es obligatoria")
    @Pattern(regexp = NombreRecurso.PATRON, message = NombreRecurso.MENSAJE)
    String cola,

    @NotBlank(message = "El exchange es obligatorio")
    @Pattern(regexp = NombreRecurso.PATRON, message = NombreRecurso.MENSAJE)
    String exchange,

    // Puede ir vacía (exchange fanout), pero no nula
    @NotNull(message = "La routing key es obligatoria (use \"\" para fanout)")
    @Size(max = 255, message = "La routing key admite hasta 255 caracteres")
    @Pattern(regexp = "^[a-zA-Z0-9.*#_-]*$", message = "La routing key solo admite letras, números y . * # _ -")
    String routingKey
) {}
