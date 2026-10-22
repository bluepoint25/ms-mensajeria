package com.tiendanative.mensajeria.common;

import java.time.Instant;

public record Notificacion(String tipo, String destinatario, String asunto, String mensaje, Instant fecha) {}
