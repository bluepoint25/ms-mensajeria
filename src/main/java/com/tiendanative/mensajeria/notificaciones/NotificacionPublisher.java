package com.tiendanative.mensajeria.notificaciones;

import com.tiendanative.mensajeria.common.ErrorTransitorio;
import com.tiendanative.mensajeria.common.Notificacion;
import com.tiendanative.mensajeria.common.ProductEvent;
import com.tiendanative.mensajeria.config.MensajeriaProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

// Productor del exchange topic: la routing key "notificacion.stock.bajo" calza con el patrón "notificacion.#"
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificacionPublisher {

    static final String DESTINATARIO_BODEGA = "bodega@tiendanative.com";

    private final RabbitTemplate rabbitTemplate;
    private final MensajeriaProperties props;

    public void reponerStock(ProductEvent evento) {
        Notificacion notificacion = new Notificacion(
            props.routingKeys().notificacionStockBajo(),
            DESTINATARIO_BODEGA,
            "Reponer stock: " + evento.nombre(),
            "El producto " + evento.nombre() + " (" + evento.productoId() + ") quedó con " + evento.stock() + " unidades.",
            Instant.now()
        );
        try {
            rabbitTemplate.convertAndSend(props.exchanges().notificaciones(), props.routingKeys().notificacionStockBajo(), notificacion);
            log.info("[PRODUCTOR] {} -> {}", props.routingKeys().notificacionStockBajo(), evento.productoId());
        } catch (AmqpException e) {
            throw new ErrorTransitorio("No se pudo publicar la notificación", e);
        }
    }
}
