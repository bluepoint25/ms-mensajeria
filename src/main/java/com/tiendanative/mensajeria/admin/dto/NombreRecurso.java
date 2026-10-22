package com.tiendanative.mensajeria.admin.dto;

final class NombreRecurso {

    // Sin espacios ni prefijo "amq." (reservado por RabbitMQ), entre 3 y 100 caracteres
    static final String PATRON = "^(?!amq\\.)[a-zA-Z0-9._-]{3,100}$";
    static final String MENSAJE = "Nombre inválido: 3-100 caracteres (letras, números, . _ -) y sin prefijo amq.";

    private NombreRecurso() {}
}
