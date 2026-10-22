package com.tiendanative.mensajeria.admin;

import com.tiendanative.mensajeria.admin.dto.QueueRequest;
import com.tiendanative.mensajeria.admin.dto.QueueRequest.TipoCola;
import com.tiendanative.mensajeria.config.MensajeriaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RabbitAdminServiceTest {

    private AmqpAdmin amqpAdmin;
    private RabbitAdminService service;

    @BeforeEach
    void setUp() {
        amqpAdmin = mock(AmqpAdmin.class);
        MensajeriaProperties props = new MensajeriaProperties(
            new MensajeriaProperties.Exchanges("productos.direct.exchange", "notificaciones.topic.exchange", "mensajeria.dlx"),
            new MensajeriaProperties.Colas("auditoria.queue", "inventario.queue", "notificaciones.queue"),
            new MensajeriaProperties.Colas("auditoria.dlq", "inventario.dlq", "notificaciones.dlq"),
            null, 3, 86_400_000, 1000);
        service = new RabbitAdminService(amqpAdmin, mock(RabbitTemplate.class), props);
    }

    @Test
    void noPermiteEliminarUnaColaDelSistema() {
        assertThatThrownBy(() -> service.eliminarCola("auditoria.queue"))
            .isInstanceOf(RecursoProtegidoException.class);
        verify(amqpAdmin, never()).deleteQueue(any());
    }

    @Test
    void eliminarUnaColaInexistenteEsNoEncontrado() {
        assertThatThrownBy(() -> service.eliminarCola("no.existe"))
            .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void noPermiteCrearUnaColaQueYaExiste() {
        when(amqpAdmin.getQueueInfo("pagos.queue")).thenReturn(new QueueInformation("pagos.queue", 0, 0));

        assertThatThrownBy(() -> service.crearCola(new QueueRequest("pagos.queue", TipoCola.QUORUM, true, false)))
            .isInstanceOf(RecursoExistenteException.class);
    }

    @Test
    void crearColaConDlqDeclaraLaDlqSuBindingYLaColaApuntandoAlDlx() {
        when(amqpAdmin.getQueueInfo("pagos.queue"))
            .thenReturn(null)
            .thenReturn(new QueueInformation("pagos.queue", 0, 0));

        service.crearCola(new QueueRequest("pagos.queue", TipoCola.QUORUM, true, true));

        ArgumentCaptor<Queue> colas = ArgumentCaptor.forClass(Queue.class);
        verify(amqpAdmin, times(2)).declareQueue(colas.capture());
        assertThat(colas.getAllValues()).extracting(Queue::getName).containsExactly("pagos.queue.dlq", "pagos.queue");

        Queue principal = colas.getAllValues().get(1);
        assertThat(principal.getArguments())
            .containsEntry("x-queue-type", "quorum")
            .containsEntry("x-dead-letter-exchange", "mensajeria.dlx")
            .containsEntry("x-dead-letter-routing-key", "pagos.queue.dlq");

        ArgumentCaptor<Binding> binding = ArgumentCaptor.forClass(Binding.class);
        verify(amqpAdmin).declareBinding(binding.capture());
        assertThat(binding.getValue().getExchange()).isEqualTo("mensajeria.dlx");
        assertThat(binding.getValue().getDestination()).isEqualTo("pagos.queue.dlq");
    }
}
