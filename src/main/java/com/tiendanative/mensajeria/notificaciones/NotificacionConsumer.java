package com.tiendanative.mensajeria.notificaciones;

import com.rabbitmq.client.Channel;
import com.tiendanative.mensajeria.common.ErrorPermanente;
import com.tiendanative.mensajeria.common.MensajeAck;
import com.tiendanative.mensajeria.common.Notificacion;
import com.tiendanative.mensajeria.monitor.EventMonitor;
import com.tiendanative.mensajeria.monitor.EventMonitor.Tipo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

// Dominio notificaciones: "envía" el correo (simulado en el log)
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificacionConsumer {

    static final String CONSUMIDOR = "NOTIFICACIONES";

    private final MensajeAck ack;
    private final EventMonitor monitor;

    @RabbitListener(id = "notificaciones", queues = "${mensajeria.colas.notificaciones}")
    public void enviar(Message message, Channel channel) throws IOException {
        String asunto = "?";
        try {
            Notificacion notificacion = ack.leer(message, Notificacion.class);
            if (notificacion.destinatario() == null || !notificacion.destinatario().contains("@")) {
                throw new ErrorPermanente("Destinatario inválido: " + notificacion.destinatario());
            }
            asunto = notificacion.asunto();

            log.info("[{}] correo a {} | {} | routing key: {}", CONSUMIDOR, notificacion.destinatario(),
                notificacion.asunto(), message.getMessageProperties().getReceivedRoutingKey());
            monitor.registrar(Tipo.NOTIFICACION, message.getMessageProperties().getConsumerQueue(), asunto,
                "Correo enviado a " + notificacion.destinatario());
            ack.confirmar(channel, message);
        } catch (Exception e) {
            ack.manejarError(channel, message, CONSUMIDOR, asunto, e);
        }
    }
}
