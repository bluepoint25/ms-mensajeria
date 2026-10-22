package com.tiendanative.mensajeria.monitor;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

// Registro en memoria de lo ocurrido en las colas, para el panel de eventos del front
@Service
public class EventMonitor {

    public enum Tipo { PROCESADO, ALERTA, NOTIFICACION, REINTENTO, DLQ }

    public record Registro(Instant fecha, Tipo tipo, String cola, String producto, String detalle) {}

    private static final int MAX_REGISTROS = 50;

    private final Deque<Registro> registros = new ConcurrentLinkedDeque<>();
    private final Map<Tipo, AtomicInteger> contadores = new EnumMap<>(Tipo.class);

    public EventMonitor() {
        for (Tipo t : Tipo.values()) {
            contadores.put(t, new AtomicInteger());
        }
    }

    public void registrar(Tipo tipo, String cola, String producto, String detalle) {
        registros.addFirst(new Registro(Instant.now(), tipo, cola, producto, detalle));
        while (registros.size() > MAX_REGISTROS) {
            registros.pollLast();
        }
        contadores.get(tipo).incrementAndGet();
    }

    public List<Registro> recientes() {
        return new ArrayList<>(registros);
    }

    public Map<Tipo, Integer> totales() {
        Map<Tipo, Integer> totales = new EnumMap<>(Tipo.class);
        contadores.forEach((tipo, n) -> totales.put(tipo, n.get()));
        return totales;
    }
}
