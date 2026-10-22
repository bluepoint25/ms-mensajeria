package com.tiendanative.mensajeria.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

// Nombres de la topología leídos de application.yml (bloque "mensajeria")
@ConfigurationProperties(prefix = "mensajeria")
public record MensajeriaProperties(
    Exchanges exchanges,
    Colas colas,
    Colas dlqs,
    RoutingKeys routingKeys,
    int maxIntentos,
    long retencionDlqMs,
    int maxMensajesPorCola
) {

    public record Exchanges(String productos, String notificaciones, String dlx) {
        public List<String> todos() {
            return List.of(productos, notificaciones, dlx);
        }
    }

    public record Colas(String auditoria, String inventario, String notificaciones) {
        public List<String> todas() {
            return List.of(auditoria, inventario, notificaciones);
        }
    }

    public record RoutingKeys(
        String productoCreado,
        String productoActualizado,
        String productoEliminado,
        String stockBajo,
        String notificacionesPatron,
        String notificacionStockBajo
    ) {}

    // Colas y exchanges que forman la topología del sistema: el administrador no permite borrarlos
    public List<String> recursosDelSistema() {
        return java.util.stream.Stream.of(colas.todas(), dlqs.todas(), exchanges.todos())
            .flatMap(List::stream)
            .toList();
    }
}
