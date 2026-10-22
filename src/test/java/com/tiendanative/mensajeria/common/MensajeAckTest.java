package com.tiendanative.mensajeria.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rabbitmq.client.Channel;
import com.tiendanative.mensajeria.config.MensajeriaProperties;
import com.tiendanative.mensajeria.monitor.EventMonitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class MensajeAckTest {

    private static final long TAG = 7L;

    private Channel channel;
    private EventMonitor monitor;
    private MensajeAck ack;

    @BeforeEach
    void setUp() {
        channel = mock(Channel.class);
        monitor = new EventMonitor();
        MensajeriaProperties props = new MensajeriaProperties(null, null, null, null, 3, 0, 0);
        ack = new MensajeAck(new ObjectMapper().registerModule(new JavaTimeModule()), props, monitor);
    }

    @Test
    void procesoExitosoHaceBasicAck() throws Exception {
        ack.confirmar(channel, mensaje("{}", null));
        verify(channel).basicAck(TAG, false);
    }

    @Test
    void errorTransitorioEnPrimerIntentoVuelveALaCola() throws Exception {
        ack.manejarError(channel, mensaje("{}", null), "TEST", "p", new ErrorTransitorio("caído"));

        verify(channel).basicNack(TAG, false, true);
        assertThat(monitor.totales().get(EventMonitor.Tipo.REINTENTO)).isEqualTo(1);
    }

    @Test
    void errorTransitorioEnUltimoIntentoVaALaDlq() throws Exception {
        // x-delivery-count = 2 -> este es el intento 3 de 3
        ack.manejarError(channel, mensaje("{}", 2L), "TEST", "p", new ErrorTransitorio("caído"));

        verify(channel).basicNack(TAG, false, false);
    }

    @Test
    void errorPermanenteVaDirectoALaDlq() throws Exception {
        ack.manejarError(channel, mensaje("{}", null), "TEST", "p", new ErrorPermanente("dato inválido"));

        verify(channel).basicNack(TAG, false, false);
        verify(channel, never()).basicNack(TAG, false, true);
    }

    @Test
    void jsonInvalidoEsErrorPermanente() {
        assertThatThrownBy(() -> ack.leer(mensaje("no-es-json", null), ProductEvent.class))
            .isInstanceOf(ErrorPermanente.class);
    }

    @Test
    void elIntentoSeCalculaDesdeElHeaderDeLaColaQuorum() {
        assertThat(ack.intento(mensaje("{}", null))).isEqualTo(1);
        assertThat(ack.intento(mensaje("{}", 1L))).isEqualTo(2);
    }

    private Message mensaje(String cuerpo, Long entregasPrevias) {
        MessageProperties props = new MessageProperties();
        props.setDeliveryTag(TAG);
        props.setConsumerQueue("auditoria.queue");
        if (entregasPrevias != null) {
            props.setHeader(MensajeAck.HEADER_ENTREGAS, entregasPrevias);
        }
        return new Message(cuerpo.getBytes(StandardCharsets.UTF_8), props);
    }
}
