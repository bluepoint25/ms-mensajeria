package com.tiendanative.mensajeria.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.tiendanative.mensajeria.config.MensajeriaProperties;
import com.tiendanative.mensajeria.monitor.EventMonitor;
import com.tiendanative.mensajeria.monitor.EventMonitor.Tipo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Las tres decisiones posibles sobre un mensaje, explícitas y registradas en el log:
 *
 *   procesó bien          -> basicAck                      (RabbitMQ lo borra)
 *   ErrorTransitorio      -> basicNack(requeue=true)       (se reentrega, hasta max-intentos)
 *   ErrorPermanente u otro-> basicNack(requeue=false)      (la cola lo manda por el DLX a su DLQ)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MensajeAck {

    // Header que agregan las colas quorum: cuántas entregas previas fallaron
    static final String HEADER_ENTREGAS = "x-delivery-count";
    private static final long BACKOFF_BASE_MS = 500;

    private final ObjectMapper objectMapper;
    private final MensajeriaProperties props;
    private final EventMonitor monitor;

    public <T> T leer(Message message, Class<T> tipo) {
        try {
            return objectMapper.readValue(message.getBody(), tipo);
        } catch (IOException e) {
            throw new ErrorPermanente("Mensaje con formato inválido: " + e.getMessage(), e);
        }
    }

    public int intento(Message message) {
        Object previas = message.getMessageProperties().getHeader(HEADER_ENTREGAS);
        return previas instanceof Number n ? n.intValue() + 1 : 1;
    }

    public void confirmar(Channel channel, Message message) throws IOException {
        channel.basicAck(tag(message), false);
    }

    public void manejarError(Channel channel, Message message, String consumidor, String producto, Exception error)
            throws IOException {
        int intento = intento(message);
        String cola = message.getMessageProperties().getConsumerQueue();

        if (error instanceof ErrorTransitorio && intento < props.maxIntentos()) {
            log.warn("[{}] intento {}/{} falló ({}): se reintenta", consumidor, intento, props.maxIntentos(), error.getMessage());
            monitor.registrar(Tipo.REINTENTO, cola, producto, "Intento " + intento + "/" + props.maxIntentos() + ": " + error.getMessage());
            esperarBackoff(intento);
            channel.basicNack(tag(message), false, true);
            return;
        }

        String motivo = error instanceof ErrorTransitorio
            ? "reintentos agotados (" + intento + "/" + props.maxIntentos() + ")"
            : "error permanente";
        log.error("[{}] {} -> enviado a la DLQ: {}", consumidor, motivo, error.getMessage());
        channel.basicNack(tag(message), false, false);
    }

    // Backoff lineal antes de devolver el mensaje: evita reintentar en ráfaga contra algo que está caído
    private void esperarBackoff(int intento) {
        try {
            Thread.sleep(BACKOFF_BASE_MS * intento);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private long tag(Message message) {
        return message.getMessageProperties().getDeliveryTag();
    }
}
