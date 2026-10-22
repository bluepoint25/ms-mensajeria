package com.tiendanative.mensajeria.dlq;

import com.rabbitmq.client.Channel;
import com.tiendanative.mensajeria.monitor.EventMonitor;
import com.tiendanative.mensajeria.monitor.EventMonitor.Tipo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

// Mensajes muertos de las tres colas: se registran en el log con su origen y motivo para analizarlos
@Component
@RequiredArgsConstructor
@Slf4j
public class DlqConsumer {

    private final EventMonitor monitor;

    @RabbitListener(id = "dlq", queues = {
        "${mensajeria.dlqs.auditoria}", "${mensajeria.dlqs.inventario}", "${mensajeria.dlqs.notificaciones}"})
    public void cuarentena(Message message, Channel channel) throws IOException {
        String dlq = message.getMessageProperties().getConsumerQueue();
        String origen = "?";
        String motivo = "?";
        // x-death lo agrega RabbitMQ al mover el mensaje: cola de origen y motivo (rejected, expired, delivery_limit...)
        List<Map<String, ?>> xDeath = message.getMessageProperties().getXDeathHeader();
        if (xDeath != null && !xDeath.isEmpty()) {
            origen = String.valueOf(xDeath.get(0).get("queue"));
            motivo = String.valueOf(xDeath.get(0).get("reason"));
        }
        String cuerpo = new String(message.getBody(), StandardCharsets.UTF_8);

        log.error("[DLQ] {} <- {} (motivo: {}) | {}", dlq, origen, motivo, cuerpo);
        monitor.registrar(Tipo.DLQ, dlq, origen, "Motivo: " + motivo);
        // El registro ya quedó en el log; el mensaje se confirma para no reprocesarlo en bucle
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }
}
