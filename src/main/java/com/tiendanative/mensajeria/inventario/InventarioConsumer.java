package com.tiendanative.mensajeria.inventario;

import com.rabbitmq.client.Channel;
import com.tiendanative.mensajeria.common.ErrorPermanente;
import com.tiendanative.mensajeria.common.MensajeAck;
import com.tiendanative.mensajeria.common.ProductEvent;
import com.tiendanative.mensajeria.monitor.EventMonitor;
import com.tiendanative.mensajeria.monitor.EventMonitor.Tipo;
import com.tiendanative.mensajeria.notificaciones.NotificacionPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

// Dominio inventario: ante stock bajo genera una alerta y pide notificar a bodega (consume y produce)
@Component
@RequiredArgsConstructor
@Slf4j
public class InventarioConsumer {

    static final String CONSUMIDOR = "INVENTARIO";

    private final MensajeAck ack;
    private final EventMonitor monitor;
    private final NotificacionPublisher notificaciones;

    @RabbitListener(id = "inventario", queues = "${mensajeria.colas.inventario}")
    public void stockBajo(Message message, Channel channel) throws IOException {
        String producto = "?";
        try {
            ProductEvent evento = ack.leer(message, ProductEvent.class);
            producto = evento.nombre();
            if (evento.stock() == null || evento.stock() < 0) {
                throw new ErrorPermanente("Stock inválido en evento de stock bajo: " + evento.stock());
            }

            log.warn("[{}] ALERTA: '{}' quedó con {} unidades", CONSUMIDOR, evento.nombre(), evento.stock());
            monitor.registrar(Tipo.ALERTA, message.getMessageProperties().getConsumerQueue(), producto,
                "Quedan " + evento.stock() + " unidades");
            // Se publica antes del ack: si el broker falla, el evento se reintenta y no se pierde la notificación
            notificaciones.reponerStock(evento);
            ack.confirmar(channel, message);
        } catch (Exception e) {
            ack.manejarError(channel, message, CONSUMIDOR, producto, e);
        }
    }
}
