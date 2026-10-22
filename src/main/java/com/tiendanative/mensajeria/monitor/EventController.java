package com.tiendanative.mensajeria.monitor;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/eventos")
@RequiredArgsConstructor
public class EventController {

    private final EventMonitor monitor;

    @GetMapping
    public Map<String, Object> eventos() {
        return Map.of("totales", monitor.totales(), "registros", monitor.recientes());
    }
}
