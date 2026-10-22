package com.tiendanative.mensajeria.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topología completa de RabbitMQ. Tres casos de uso, cada uno con su cola y su DLQ:
 *
 *   productos.direct ──producto.* / stock.bajo──► auditoria.queue      ──► auditoria.dlq
 *   productos.direct ──stock.bajo──────────────► inventario.queue     ──► inventario.dlq
 *   notificaciones.topic ──notificacion.#──────► notificaciones.queue ──► notificaciones.dlq
 *
 * Las colas son quorum (replicadas en el cluster). Un mensaje muere y pasa por mensajeria.dlx
 * a su DLQ cuando el consumidor lo rechaza sin requeue o supera x-delivery-limit.
 */
@Configuration
public class RabbitTopologyConfig {

    private final MensajeriaProperties props;

    public RabbitTopologyConfig(MensajeriaProperties props) {
        this.props = props;
    }

    // ===== Exchanges =====

    @Bean
    public DirectExchange productosExchange() {
        return new DirectExchange(props.exchanges().productos(), true, false);
    }

    @Bean
    public TopicExchange notificacionesExchange() {
        return new TopicExchange(props.exchanges().notificaciones(), true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(props.exchanges().dlx(), true, false);
    }

    // ===== Caso 1: auditoría de todos los eventos de producto =====

    @Bean
    public Queue auditoriaQueue() {
        return colaPrincipal(props.colas().auditoria(), props.dlqs().auditoria());
    }

    @Bean
    public Queue auditoriaDlq() {
        return dlq(props.dlqs().auditoria());
    }

    @Bean
    public Declarables auditoriaBindings(DirectExchange productosExchange, Queue auditoriaQueue,
                                         DirectExchange deadLetterExchange, Queue auditoriaDlq) {
        MensajeriaProperties.RoutingKeys rk = props.routingKeys();
        return new Declarables(
            BindingBuilder.bind(auditoriaQueue).to(productosExchange).with(rk.productoCreado()),
            BindingBuilder.bind(auditoriaQueue).to(productosExchange).with(rk.productoActualizado()),
            BindingBuilder.bind(auditoriaQueue).to(productosExchange).with(rk.productoEliminado()),
            BindingBuilder.bind(auditoriaQueue).to(productosExchange).with(rk.stockBajo()),
            BindingBuilder.bind(auditoriaDlq).to(deadLetterExchange).with(props.dlqs().auditoria())
        );
    }

    // ===== Caso 2: inventario reacciona solo al stock bajo =====

    @Bean
    public Queue inventarioQueue() {
        return colaPrincipal(props.colas().inventario(), props.dlqs().inventario());
    }

    @Bean
    public Queue inventarioDlq() {
        return dlq(props.dlqs().inventario());
    }

    @Bean
    public Declarables inventarioBindings(DirectExchange productosExchange, Queue inventarioQueue,
                                          DirectExchange deadLetterExchange, Queue inventarioDlq) {
        return new Declarables(
            BindingBuilder.bind(inventarioQueue).to(productosExchange).with(props.routingKeys().stockBajo()),
            BindingBuilder.bind(inventarioDlq).to(deadLetterExchange).with(props.dlqs().inventario())
        );
    }

    // ===== Caso 3: notificaciones, enrutadas por patrón en el exchange topic =====

    @Bean
    public Queue notificacionesQueue() {
        return colaPrincipal(props.colas().notificaciones(), props.dlqs().notificaciones());
    }

    @Bean
    public Queue notificacionesDlq() {
        return dlq(props.dlqs().notificaciones());
    }

    @Bean
    public Declarables notificacionesBindings(TopicExchange notificacionesExchange, Queue notificacionesQueue,
                                              DirectExchange deadLetterExchange, Queue notificacionesDlq) {
        return new Declarables(
            BindingBuilder.bind(notificacionesQueue).to(notificacionesExchange).with(props.routingKeys().notificacionesPatron()),
            BindingBuilder.bind(notificacionesDlq).to(deadLetterExchange).with(props.dlqs().notificaciones())
        );
    }

    // ===== Plantillas =====

    private Queue colaPrincipal(String nombre, String dlq) {
        return QueueBuilder.durable(nombre)
            .quorum()
            .deadLetterExchange(props.exchanges().dlx())
            .deadLetterRoutingKey(dlq)
            // Red de seguridad: aunque un consumidor nunca decida, el broker no reentrega para siempre
            .deliveryLimit(props.maxIntentos())
            .maxLength(props.maxMensajesPorCola())
            .build();
    }

    // Política de retención: la DLQ conserva los mensajes fallidos por un tiempo acotado
    private Queue dlq(String nombre) {
        return QueueBuilder.durable(nombre)
            .quorum()
            .ttl((int) props.retencionDlqMs())
            .build();
    }

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
