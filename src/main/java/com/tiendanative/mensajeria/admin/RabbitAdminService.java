package com.tiendanative.mensajeria.admin;

import com.tiendanative.mensajeria.admin.dto.BindingRequest;
import com.tiendanative.mensajeria.admin.dto.EstadoCola;
import com.tiendanative.mensajeria.admin.dto.ExchangeRequest;
import com.tiendanative.mensajeria.admin.dto.QueueRequest;
import com.tiendanative.mensajeria.config.MensajeriaProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Stream;

// Toda la administración de RabbitMQ pasa por aquí: los controladores no conocen AmqpAdmin
@Service
@RequiredArgsConstructor
@Slf4j
public class RabbitAdminService {

    static final String SUFIJO_DLQ = ".dlq";

    private final AmqpAdmin amqpAdmin;
    private final RabbitTemplate rabbitTemplate;
    private final MensajeriaProperties props;

    // ===== Colas =====

    public List<EstadoCola> colasDelSistema() {
        return Stream.of(props.colas().todas(), props.dlqs().todas())
            .flatMap(List::stream)
            .map(this::estadoCola)
            .toList();
    }

    public EstadoCola obtenerCola(String nombre) {
        return estadoCola(nombre);
    }

    public EstadoCola crearCola(QueueRequest request) {
        exigirQueNoExista(request.nombre());
        if (request.conDlq()) {
            exigirQueNoExista(request.nombre() + SUFIJO_DLQ);
        }

        QueueBuilder builder = request.durable()
            ? QueueBuilder.durable(request.nombre())
            : QueueBuilder.nonDurable(request.nombre());
        if (request.tipo() == QueueRequest.TipoCola.QUORUM) {
            builder.quorum();
        }

        if (request.conDlq()) {
            String dlq = request.nombre() + SUFIJO_DLQ;
            Queue colaDlq = request.tipo() == QueueRequest.TipoCola.QUORUM
                ? QueueBuilder.durable(dlq).quorum().build()
                : QueueBuilder.durable(dlq).build();
            amqpAdmin.declareQueue(colaDlq);
            amqpAdmin.declareBinding(new Binding(dlq, Binding.DestinationType.QUEUE, props.exchanges().dlx(), dlq, null));
            builder.deadLetterExchange(props.exchanges().dlx()).deadLetterRoutingKey(dlq);
            log.info("[ADMIN] DLQ creada: {}", dlq);
        }

        amqpAdmin.declareQueue(builder.build());
        log.info("[ADMIN] Cola creada: {} ({}, durable={}, conDlq={})",
            request.nombre(), request.tipo(), request.durable(), request.conDlq());
        return estadoCola(request.nombre());
    }

    public void eliminarCola(String nombre) {
        exigirQueNoSeaDelSistema(nombre);
        exigirCola(nombre);
        amqpAdmin.deleteQueue(nombre);
        log.info("[ADMIN] Cola eliminada: {}", nombre);
    }

    public int purgarCola(String nombre) {
        exigirCola(nombre);
        int eliminados = amqpAdmin.purgeQueue(nombre);
        log.info("[ADMIN] Cola purgada: {} ({} mensajes)", nombre, eliminados);
        return eliminados;
    }

    // ===== Exchanges =====

    public void crearExchange(ExchangeRequest request) {
        if (existeExchange(request.nombre())) {
            throw new RecursoExistenteException("El exchange '" + request.nombre() + "' ya existe");
        }
        Exchange exchange = switch (request.tipo()) {
            case DIRECT -> new DirectExchange(request.nombre(), request.durable(), false);
            case TOPIC -> new TopicExchange(request.nombre(), request.durable(), false);
            case FANOUT -> new FanoutExchange(request.nombre(), request.durable(), false);
        };
        amqpAdmin.declareExchange(exchange);
        log.info("[ADMIN] Exchange creado: {} ({})", request.nombre(), request.tipo());
    }

    public void eliminarExchange(String nombre) {
        exigirQueNoSeaDelSistema(nombre);
        if (!existeExchange(nombre)) {
            throw new RecursoNoEncontradoException("El exchange '" + nombre + "' no existe");
        }
        amqpAdmin.deleteExchange(nombre);
        log.info("[ADMIN] Exchange eliminado: {}", nombre);
    }

    // ===== Bindings =====

    public void crearBinding(BindingRequest request) {
        exigirCola(request.cola());
        if (!existeExchange(request.exchange())) {
            throw new RecursoNoEncontradoException("El exchange '" + request.exchange() + "' no existe");
        }
        amqpAdmin.declareBinding(binding(request));
        log.info("[ADMIN] Binding creado: {} -> {} [{}]", request.exchange(), request.cola(), request.routingKey());
    }

    public void eliminarBinding(BindingRequest request) {
        exigirQueNoSeaDelSistema(request.cola());
        amqpAdmin.removeBinding(binding(request));
        log.info("[ADMIN] Binding eliminado: {} -> {} [{}]", request.exchange(), request.cola(), request.routingKey());
    }

    // ===== Reglas =====

    private Binding binding(BindingRequest request) {
        return new Binding(request.cola(), Binding.DestinationType.QUEUE, request.exchange(), request.routingKey(), null);
    }

    private EstadoCola estadoCola(String nombre) {
        QueueInformation info = exigirCola(nombre);
        return new EstadoCola(nombre, info.getMessageCount(), info.getConsumerCount(), esDelSistema(nombre));
    }

    private QueueInformation exigirCola(String nombre) {
        QueueInformation info = amqpAdmin.getQueueInfo(nombre);
        if (info == null) {
            throw new RecursoNoEncontradoException("La cola '" + nombre + "' no existe");
        }
        return info;
    }

    private void exigirQueNoExista(String cola) {
        if (amqpAdmin.getQueueInfo(cola) != null) {
            throw new RecursoExistenteException("La cola '" + cola + "' ya existe");
        }
    }

    // La topología base la declara la aplicación al arrancar: borrarla rompería el flujo de mensajes
    private void exigirQueNoSeaDelSistema(String nombre) {
        if (esDelSistema(nombre)) {
            throw new RecursoProtegidoException("'" + nombre + "' es parte de la topología del sistema y no se puede modificar");
        }
    }

    private boolean esDelSistema(String nombre) {
        return props.recursosDelSistema().contains(nombre);
    }

    private boolean existeExchange(String nombre) {
        try {
            rabbitTemplate.execute(channel -> channel.exchangeDeclarePassive(nombre));
            return true;
        } catch (AmqpException e) {
            return false;
        }
    }
}
