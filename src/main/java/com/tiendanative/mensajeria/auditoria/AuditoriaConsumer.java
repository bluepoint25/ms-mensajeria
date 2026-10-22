package com.tiendanative.mensajeria.auditoria;

import com.rabbitmq.client.Channel;
import com.tiendanative.mensajeria.common.ErrorPermanente;
import com.tiendanative.mensajeria.common.ErrorTransitorio;
import com.tiendanative.mensajeria.common.MensajeAck;
import com.tiendanative.mensajeria.common.ProductEvent;
import com.tiendanative.mensajeria.monitor.EventMonitor;
import com.tiendanative.mensajeria.monitor.EventMonitor.Tipo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

// Dominio auditoría: deja registro de todo cambio sobre productos
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditoriaConsumer {

    static final String CONSUMIDOR = "AUDITORIA";
    // Fallas simuladas para la demo: "FALLA" en el nombre -> transitoria, "INVALIDO" -> permanente
    static final String SIMULA_TRANSITORIO = "FALLA";
    static final String SIMULA_PERMANENTE = "INVALIDO";

    private final MensajeAck ack;
    private final EventMonitor monitor;

    @RabbitListener(id = "auditoria", queues = "${mensajeria.colas.auditoria}")
    public void auditar(Message message, Channel channel) throws IOException {
        String producto = "?";
        try {
            ProductEvent evento = ack.leer(message, ProductEvent.class);
            producto = evento.nombre();
            validar(evento);

            log.info("[{}] {} | {} ({}) stock={}", CONSUMIDOR, evento.tipo(), evento.nombre(), evento.productoId(), evento.stock());
            monitor.registrar(Tipo.PROCESADO, message.getMessageProperties().getConsumerQueue(), producto,
                evento.tipo() + " auditado (stock " + evento.stock() + ")");
            ack.confirmar(channel, message);
        } catch (Exception e) {
            ack.manejarError(channel, message, CONSUMIDOR, producto, e);
        }
    }

    private void validar(ProductEvent evento) {
        if (evento.productoId() == null || evento.tipo() == null) {
            throw new ErrorPermanente("Evento sin productoId o tipo");
        }
        String nombre = evento.nombre() == null ? "" : evento.nombre().toUpperCase();
        if (nombre.contains(SIMULA_PERMANENTE)) {
            throw new ErrorPermanente("Datos del producto inválidos (simulado)");
        }
        if (nombre.contains(SIMULA_TRANSITORIO)) {
            throw new ErrorTransitorio("Servicio de auditoría no disponible (simulado)");
        }
    }
}
