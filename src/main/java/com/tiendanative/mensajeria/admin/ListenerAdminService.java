package com.tiendanative.mensajeria.admin;

import com.tiendanative.mensajeria.admin.dto.EstadoListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.stereotype.Service;

import java.util.List;

// Detener un consumidor lo desconecta de su cola: los mensajes quedan esperando sin perderse
@Service
@RequiredArgsConstructor
@Slf4j
public class ListenerAdminService {

    private final RabbitListenerEndpointRegistry registry;

    public List<EstadoListener> listar() {
        return registry.getListenerContainerIds().stream()
            .sorted()
            .map(id -> new EstadoListener(id, contenedor(id).isRunning()))
            .toList();
    }

    public EstadoListener iniciar(String id) {
        MessageListenerContainer container = contenedor(id);
        container.start();
        log.info("[ADMIN] Consumidor iniciado: {}", id);
        return new EstadoListener(id, container.isRunning());
    }

    public EstadoListener detener(String id) {
        MessageListenerContainer container = contenedor(id);
        container.stop();
        log.info("[ADMIN] Consumidor detenido: {}", id);
        return new EstadoListener(id, container.isRunning());
    }

    private MessageListenerContainer contenedor(String id) {
        MessageListenerContainer container = registry.getListenerContainer(id);
        if (container == null) {
            throw new RecursoNoEncontradoException("No existe el consumidor '" + id + "'");
        }
        return container;
    }
}
