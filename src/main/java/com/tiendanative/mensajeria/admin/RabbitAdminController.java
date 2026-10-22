package com.tiendanative.mensajeria.admin;

import com.tiendanative.mensajeria.admin.dto.BindingRequest;
import com.tiendanative.mensajeria.admin.dto.EstadoCola;
import com.tiendanative.mensajeria.admin.dto.EstadoListener;
import com.tiendanative.mensajeria.admin.dto.ExchangeRequest;
import com.tiendanative.mensajeria.admin.dto.QueueRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * API del administrador de RabbitMQ.
 *
 *   GET    /queues                  estado de las colas del sistema
 *   GET    /queues/{nombre}         mensajes y consumidores de una cola
 *   POST   /queues                  crea una cola (opcionalmente con su DLQ)      201
 *   DELETE /queues/{nombre}         elimina una cola                              204
 *   POST   /queues/{nombre}/purge   vacía una cola
 *   POST   /exchanges               crea un exchange direct, topic o fanout       201
 *   DELETE /exchanges/{nombre}      elimina un exchange                           204
 *   POST   /bindings                enlaza una cola a un exchange                 201
 *   DELETE /bindings                elimina un binding (mismo cuerpo que el POST) 204
 *   GET    /listeners               consumidores y si están activos
 *   POST   /listeners/{id}/start    inicia un consumidor
 *   POST   /listeners/{id}/stop     detiene un consumidor
 *
 * Errores: 400 validación, 404 no existe, 409 ya existe o es parte del sistema.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class RabbitAdminController {

    private final RabbitAdminService rabbitAdmin;
    private final ListenerAdminService listeners;

    @GetMapping("/queues")
    public List<EstadoCola> colas() {
        return rabbitAdmin.colasDelSistema();
    }

    @GetMapping("/queues/{nombre}")
    public EstadoCola cola(@PathVariable String nombre) {
        return rabbitAdmin.obtenerCola(nombre);
    }

    @PostMapping("/queues")
    @ResponseStatus(HttpStatus.CREATED)
    public EstadoCola crearCola(@Valid @RequestBody QueueRequest request) {
        return rabbitAdmin.crearCola(request);
    }

    @DeleteMapping("/queues/{nombre}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarCola(@PathVariable String nombre) {
        rabbitAdmin.eliminarCola(nombre);
    }

    @PostMapping("/queues/{nombre}/purge")
    public Map<String, Object> purgarCola(@PathVariable String nombre) {
        return Map.of("cola", nombre, "eliminados", rabbitAdmin.purgarCola(nombre));
    }

    @PostMapping("/exchanges")
    @ResponseStatus(HttpStatus.CREATED)
    public ExchangeRequest crearExchange(@Valid @RequestBody ExchangeRequest request) {
        rabbitAdmin.crearExchange(request);
        return request;
    }

    @DeleteMapping("/exchanges/{nombre}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarExchange(@PathVariable String nombre) {
        rabbitAdmin.eliminarExchange(nombre);
    }

    @PostMapping("/bindings")
    @ResponseStatus(HttpStatus.CREATED)
    public BindingRequest crearBinding(@Valid @RequestBody BindingRequest request) {
        rabbitAdmin.crearBinding(request);
        return request;
    }

    @DeleteMapping("/bindings")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarBinding(@Valid @RequestBody BindingRequest request) {
        rabbitAdmin.eliminarBinding(request);
    }

    @GetMapping("/listeners")
    public List<EstadoListener> listeners() {
        return listeners.listar();
    }

    @PostMapping("/listeners/{id}/start")
    public EstadoListener iniciar(@PathVariable String id) {
        return listeners.iniciar(id);
    }

    @PostMapping("/listeners/{id}/stop")
    public EstadoListener detener(@PathVariable String id) {
        return listeners.detener(id);
    }
}
