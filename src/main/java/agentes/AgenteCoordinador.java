package agentes;

import jade.core.Agent;
import jade.core.AID;

import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;

import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import modelos.ResultadoAbastecimiento.Estado;

import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;

public class AgenteCoordinador extends Agent {

    private static final String ONTOLOGIA_INVENTARIO =
        "gestion-inventario";

    private static final String ONTOLOGIA_ABASTECIMIENTO =
        "gestion-abastecimiento";

    private static final String ONTOLOGIA_COORDINACION = "coordinacion-escenarios";

    private static final long TIMEOUT = 30000L;

    private AID agenteInventario;
    private AID agenteAbastecimiento;



    private final Map<String, EscenarioPendiente>
        pendientes = new HashMap<>();

    private enum Etapa {
        INVENTARIO,
        ABASTECIMIENTO
    }

    private static class EscenarioPendiente {

        String id;
        String producto;
        String ventas;

        int stockActual;
        int stockSeguridad;

        int pronostico;
        int reposicion;

        long tiempoInicio;

        String idSolicitud;
        AID destinatario;
        ACLMessage solicitudOriginal;

        Etapa etapa;
    }

    @Override
    protected void setup() {

        System.out.println(
            "[COORDINADOR] Agente iniciado: "
            + getAID().getName()
        );

        System.out.println(
            "[COORDINADOR] Version 1.4"
        );

        if (!registrarServicio()) {
            doDelete();
            return;
        }

        // Descubrimiento de servicios.

        addBehaviour(new TickerBehaviour(this, 2000) {

            @Override
            protected void onTick() {

                if (agenteInventario == null) {

                    agenteInventario =
                        buscarServicio(ONTOLOGIA_INVENTARIO);
                }

                if (agenteAbastecimiento == null) {

                    agenteAbastecimiento =
                        buscarServicio(
                            ONTOLOGIA_ABASTECIMIENTO
                        );
                }


            }
        });

        // Recepcion de respuestas ACL.

        addBehaviour(new CyclicBehaviour(this) {

            @Override
            public void action() {

                MessageTemplate filtro =
                    MessageTemplate.or(

                        MessageTemplate.MatchOntology(
                            ONTOLOGIA_INVENTARIO
                        ),

                        MessageTemplate.MatchOntology(
                            ONTOLOGIA_ABASTECIMIENTO
                        )
                    );

                ACLMessage mensaje = receive(filtro);

                if (mensaje == null) {
                    block();
                    return;
                }

                procesarRespuesta(mensaje);
            }
        });

        addBehaviour(new CyclicBehaviour(this) {

            @Override
            public void action() {

                MessageTemplate filtro =
                    MessageTemplate.and(
                        MessageTemplate.MatchOntology(
                            ONTOLOGIA_COORDINACION
                        ),
                        MessageTemplate.MatchPerformative(
                            ACLMessage.REQUEST
                        )
                    );

                ACLMessage solicitud = receive(filtro);

                if (solicitud == null) {
                    block();
                    return;
                }

                recibirEscenario(solicitud);
            }
        });

        // Control de tiempos de espera.

        addBehaviour(new TickerBehaviour(this, 1000) {

            @Override
            protected void onTick() {

                verificarTiemposEspera();
            }
        });

        System.out.println(
            "[COORDINADOR] Disponible para escenarios"
        );
    }

private void recibirEscenario(ACLMessage solicitud) {

    try {

        String contenido = solicitud.getContent();

        if (contenido == null) {
            throw new IllegalArgumentException(
                "SOLICITUD_VACIA"
            );
        }

        String[] datos = contenido.split(";", -1);

        if (datos.length != 4) {
            throw new IllegalArgumentException(
                "FORMATO_INVALIDO"
            );
        }

        String producto = datos[0].trim();
        String ventas = datos[1].trim();

        int stockActual =
            Integer.parseInt(datos[2].trim());

        int stockSeguridad =
            Integer.parseInt(datos[3].trim());

        if (producto.isEmpty()
                || ventas.isEmpty()
                || stockActual < 0
                || stockSeguridad < 0) {

            throw new IllegalArgumentException(
                "DATOS_INVALIDOS"
            );
        }

        for (String venta : ventas.split(",", -1)) {

            if (Integer.parseInt(venta.trim()) < 0) {

                throw new IllegalArgumentException(
                    "VENTAS_NEGATIVAS"
                );
            }
        }

        if (agenteInventario == null) {

            agenteInventario =
                buscarServicio(ONTOLOGIA_INVENTARIO);
        }

        if (agenteInventario == null) {

            responder(
                solicitud,
                ACLMessage.REFUSE,
                "SERVICIO_INVENTARIO_NO_DISPONIBLE"
            );

            return;
        }

        responder(
            solicitud,
            ACLMessage.AGREE,
            "ESCENARIO_ACEPTADO"
        );

        procesarEscenario(
            producto,
            ventas,
            stockActual,
            stockSeguridad,
            solicitud
        );

    } catch (IllegalArgumentException e) {

        responder(
            solicitud,
            ACLMessage.REFUSE,
            "DATOS_INVALIDOS: " + e.getMessage()
        );
    }
}


    // ==================================================
    // INICIO DEL ESCENARIO
    // ==================================================

    private void procesarEscenario(
            String producto,
            String ventas,
            int stockActual,
            int stockSeguridad,
            ACLMessage solicitudOriginal) {

        EscenarioPendiente contexto =
            new EscenarioPendiente();

        contexto.id =
            "escenario-" + UUID.randomUUID();

        contexto.producto = producto;
        contexto.ventas = ventas;
        contexto.stockActual = stockActual;
        contexto.stockSeguridad = stockSeguridad;
        contexto.solicitudOriginal = solicitudOriginal;

        pendientes.put(contexto.id, contexto);

        System.out.println(
            "[COORDINADOR] Nuevo escenario: "
            + contexto.id
        );

        enviarSolicitud(
            contexto,
            agenteInventario,
            ONTOLOGIA_INVENTARIO,
            Etapa.INVENTARIO,
            producto + ";"
                + ventas + ";"
                + stockActual + ";"
                + stockSeguridad
        );
    }

    // ==================================================
    // ENVIO DE SOLICITUDES ACL
    // ==================================================

    private void enviarSolicitud(
            EscenarioPendiente contexto,
            AID destinatario,
            String ontologia,
            Etapa etapa,
            String contenido) {

        contexto.destinatario = destinatario;
        contexto.etapa = etapa;

        contexto.idSolicitud =
            UUID.randomUUID().toString();

        contexto.tiempoInicio =
            System.currentTimeMillis();

        ACLMessage solicitud =
            new ACLMessage(ACLMessage.REQUEST);

        solicitud.addReceiver(destinatario);

        solicitud.setOntology(ontologia);

        solicitud.setConversationId(contexto.id);

        solicitud.setReplyWith(
            contexto.idSolicitud
        );

        solicitud.setContent(contenido);

        send(solicitud);

        System.out.println(
            "[COORDINADOR] ACL REQUEST enviado a: "
            + destinatario.getName()
        );

        System.out.println(
            "[COORDINADOR] Conversacion: "
            + contexto.id
        );

        System.out.println(
            "[COORDINADOR] Etapa: "
            + contexto.etapa
        );

        System.out.println(
            "[COORDINADOR] Datos: "
            + contenido
        );
    }

    // ==================================================
    // RECEPCION Y CORRELACION DE RESPUESTAS
    // ==================================================

    private void procesarRespuesta(
            ACLMessage mensaje) {

        String id = mensaje.getConversationId();

        if (id == null) {
            return;
        }

        EscenarioPendiente contexto =
            pendientes.get(id);

        if (contexto == null) {
            return;
        }

        String ontologiaEsperada =
            contexto.etapa == Etapa.INVENTARIO
                ? ONTOLOGIA_INVENTARIO
                : ONTOLOGIA_ABASTECIMIENTO;

        if (!contexto.destinatario.equals(
                    mensaje.getSender())

                || !contexto.idSolicitud.equals(
                    mensaje.getInReplyTo())

                || !ontologiaEsperada.equals(
                    mensaje.getOntology())) {

            System.err.println(
                "[COORDINADOR] Respuesta no correlacionada"
            );

            return;
        }

        int performativa = mensaje.getPerformative();

        if (performativa == ACLMessage.AGREE) {
            return;
        }

        if (performativa != ACLMessage.INFORM) {

            pendientes.remove(id);

            System.err.println(
                "[COORDINADOR] Escenario no completado: "
                + id + " | " + mensaje.getContent()
            );

            responder(contexto.solicitudOriginal, ACLMessage.FAILURE,
                "ERROR_" + contexto.etapa + ": " + mensaje.getContent());

            return;
        }

        System.out.println(
            "[COORDINADOR] ACL INFORM recibido de: "
            + mensaje.getSender().getName()
        );

        try {

            if (contexto.etapa == Etapa.INVENTARIO) {

                procesarRespuestaInventario(
                    contexto,
                    mensaje.getContent()
                );

            } else {

                procesarRespuestaAbastecimiento(
                    contexto,
                    mensaje.getContent()
                );
            }

        } catch (IllegalArgumentException e) {

            pendientes.remove(id);
            responder(contexto.solicitudOriginal, ACLMessage.FAILURE,
                "ERROR_PROCESAMIENTO: " + e.getMessage());

            System.err.println(
                "[COORDINADOR] Error en escenario "
                + id + ": " + e.getMessage()
            );
        }
    }

    // ==================================================
    // RESULTADO DE INVENTARIO
    // ==================================================

    private void procesarRespuestaInventario(
            EscenarioPendiente contexto,
            String contenido) {

        if (contenido == null) {

            throw new IllegalArgumentException(
                "RESPUESTA_VACIA"
            );
        }

        String[] datos =
            contenido.split(";", -1);

        if (datos.length != 3
                || !contexto.producto.equals(datos[0])) {

            throw new IllegalArgumentException(
                "RESPUESTA_INVENTARIO_INVALIDA"
            );
        }

        contexto.pronostico =
            Integer.parseInt(datos[1]);

        contexto.reposicion =
            Integer.parseInt(datos[2]);

        if (contexto.pronostico < 0
                || contexto.reposicion < 0) {

            throw new IllegalArgumentException(
                "RESULTADOS_NEGATIVOS"
            );
        }

        System.out.println(
            "[COORDINADOR] Pronostico: "
            + contexto.pronostico
        );

        System.out.println(
            "[COORDINADOR] Reposicion necesaria: "
            + contexto.reposicion
        );

        // No se necesita evaluar proveedores.

        if (contexto.reposicion == 0) {

            mostrarResultado(
                contexto,
                Estado.NO_REQUIERE_COMPRA,
                "",
                0.0,
                0.0,
                0,
                0.0
            );

            pendientes.remove(contexto.id);

            return;
        }

        // Continuar el mismo escenario.

        if (agenteAbastecimiento == null) {

            agenteAbastecimiento =
                buscarServicio(
                    ONTOLOGIA_ABASTECIMIENTO
                );
        }

        if (agenteAbastecimiento == null) {

            throw new IllegalArgumentException(
                "SERVICIO_ABASTECIMIENTO_NO_DISPONIBLE"
            );
        }

        enviarSolicitud(
            contexto,
            agenteAbastecimiento,
            ONTOLOGIA_ABASTECIMIENTO,
            Etapa.ABASTECIMIENTO,
            contexto.producto + ";"
                + contexto.reposicion
        );
    }

    // ==================================================
    // RESULTADO DE ABASTECIMIENTO
    // ==================================================

    private void procesarRespuestaAbastecimiento(
            EscenarioPendiente contexto,
            String contenido) {

        if (contenido == null) {

            throw new IllegalArgumentException(
                "RESPUESTA_VACIA"
            );
        }

        String[] datos =
            contenido.split(";", -1);

        if (datos.length != 8
                || !contexto.producto.equals(datos[1])) {

            throw new IllegalArgumentException(
                "RESPUESTA_ABASTECIMIENTO_INVALIDA"
            );
        }

        Estado estado =
            Estado.valueOf(datos[0]);

        int cantidad =
            Integer.parseInt(datos[2]);

        String proveedor = datos[3];

        double precio =
            Double.parseDouble(datos[4]);

        double costo =
            Double.parseDouble(datos[5]);

        int plazo =
            Integer.parseInt(datos[6]);

        double puntaje =
            Double.parseDouble(datos[7]);

        if (cantidad != contexto.reposicion

                || !Double.isFinite(precio)
                || !Double.isFinite(costo)
                || !Double.isFinite(puntaje)

                || precio < 0
                || costo < 0
                || plazo < 0
                || puntaje < 0
                || puntaje > 1) {

            throw new IllegalArgumentException(
                "RESULTADO_ABASTECIMIENTO_INCONSISTENTE"
            );
        }

        if (estado == Estado.PROVEEDOR_SELECCIONADO) {

            if (proveedor.trim().isEmpty()
                    || precio <= 0
                    || plazo <= 0
                    || puntaje <= 0
                    || Math.abs(costo - cantidad * precio)
                        > 0.011) {

                throw new IllegalArgumentException(
                    "DATOS_PROVEEDOR_INVALIDOS"
                );
            }

        } else if (
                estado == Estado.SIN_PROVEEDOR_ELEGIBLE) {

            if (!proveedor.isEmpty()
                    || precio != 0
                    || costo != 0
                    || plazo != 0
                    || puntaje != 0) {

                throw new IllegalArgumentException(
                    "ESTADO_PROVEEDOR_INCONSISTENTE"
                );
            }

        } else {

            throw new IllegalArgumentException(
                "ESTADO_NO_COMPATIBLE_CON_REPOSICION"
            );
        }

        mostrarResultado(
            contexto,
            estado,
            proveedor,
            precio,
            costo,
            plazo,
            puntaje
        );

        pendientes.remove(contexto.id);
    }

    // ==================================================
    // RESULTADO INTEGRAL
    // ==================================================

    private void mostrarResultado(
            EscenarioPendiente contexto,
            Estado estado,
            String proveedor,
            double precio,
            double costo,
            int plazo,
            double puntaje) {

        System.out.println(
            "=========================================="
        );

        System.out.println(
            " RESULTADO INTEGRAL DEL ESCENARIO"
        );

        System.out.println(
            "=========================================="
        );

        System.out.println(
            "Escenario: " + contexto.id
        );

        System.out.println(
            "Producto: " + contexto.producto
        );

        System.out.println(
            "Ventas historicas: " + contexto.ventas
        );

        System.out.println(
            "Pronostico: " + contexto.pronostico
        );

        System.out.println(
            "Stock actual: " + contexto.stockActual
        );

        System.out.println(
            "Stock de seguridad: "
            + contexto.stockSeguridad
        );

        System.out.println(
            "Reposicion necesaria: "
            + contexto.reposicion
        );

        System.out.println(
            "Estado: " + estado
        );

        switch (estado) {

            case PROVEEDOR_SELECCIONADO:

                System.out.println(
                    "Proveedor: " + proveedor
                );

                System.out.printf(
                    Locale.US,
                    "Precio unitario: S/ %.2f%n",
                    precio
                );

                System.out.printf(
                    Locale.US,
                    "Costo total: S/ %.2f%n",
                    costo
                );

                System.out.println(
                    "Plazo de entrega: "
                    + plazo + " dias"
                );

                System.out.printf(
                    Locale.US,
                    "Puntaje obtenido: %.4f%n",
                    puntaje
                );

                break;

            case NO_REQUIERE_COMPRA:

                System.out.println(
                    "Inventario suficiente"
                );

                break;

            case SIN_PROVEEDOR_ELEGIBLE:

                System.out.println(
                    "ALERTA: No existen proveedores "
                    + "con disponibilidad suficiente"
                );

                break;
        }

        System.out.println(
            "=========================================="
        );

        responder(contexto.solicitudOriginal, ACLMessage.INFORM,
            String.format(Locale.US, "%s;%s;%d;%d;%s;%.2f;%.2f;%d;%.4f",
                estado, contexto.producto, contexto.pronostico,
                contexto.reposicion, proveedor, precio, costo, plazo, puntaje));

        System.out.println(
            "[COORDINADOR] Escenario finalizado: "
            + contexto.id
        );
    }

    private void responder(ACLMessage original, int performativa, String contenido) {
        ACLMessage respuesta = original.createReply();
        respuesta.setPerformative(performativa);
        respuesta.setContent(contenido);
        send(respuesta);
    }

    // ==================================================
    // DESCUBRIMIENTO DE SERVICIOS
    // ==================================================

    private AID buscarServicio(String tipo) {

        DFAgentDescription plantilla =
            new DFAgentDescription();

        ServiceDescription servicio =
            new ServiceDescription();

        servicio.setType(tipo);

        plantilla.addServices(servicio);

        try {

            DFAgentDescription[] resultados =
                DFService.search(this, plantilla);

            if (resultados.length > 0) {

                AID agente =
                    resultados[0].getName();

                System.out.println(
                    "[COORDINADOR] Servicio encontrado: "
                    + agente.getName()
                );

                return agente;
            }

        } catch (FIPAException e) {

            System.err.println(
                "[COORDINADOR] Error DF: "
                + e.getMessage()
            );
        }

        return null;
    }

    // ==================================================
    // CONTROL DE TIEMPOS DE ESPERA
    // ==================================================

    private void verificarTiemposEspera() {

        long ahora =
            System.currentTimeMillis();

        Iterator<Map.Entry<String, EscenarioPendiente>>
            iterador =
                pendientes.entrySet().iterator();

        while (iterador.hasNext()) {

            EscenarioPendiente contexto =
                iterador.next().getValue();

            if (ahora - contexto.tiempoInicio
                    >= TIMEOUT) {

                iterador.remove();
                responder(contexto.solicitudOriginal, ACLMessage.FAILURE,
                    "TIMEOUT_" + contexto.etapa);

                System.err.println(
                    "[COORDINADOR] TIMEOUT: "
                    + contexto.id
                    + " | Etapa: "
                    + contexto.etapa
                );

                if (contexto.etapa == Etapa.INVENTARIO) {

                    agenteInventario = null;

                } else {

                    agenteAbastecimiento = null;
                }
            }
        }
    }

    // ==================================================
    // REGISTRO EN DF
    // ==================================================

    private boolean registrarServicio() {

        DFAgentDescription descripcion =
            new DFAgentDescription();

        descripcion.setName(getAID());

        ServiceDescription servicio =
            new ServiceDescription();

        servicio.setType(
            "coordinacion-escenarios"
        );

        servicio.setName(
            "servicio-coordinador"
        );

        descripcion.addServices(servicio);

        try {

            DFService.register(this, descripcion);

            System.out.println(
                "[COORDINADOR] Servicio registrado en DF"
            );

            return true;

        } catch (FIPAException e) {

            e.printStackTrace();

            return false;
        }
    }

    @Override
    protected void takeDown() {

        try {

            DFService.deregister(this);

        } catch (FIPAException e) {

            e.printStackTrace();
        }

        pendientes.clear();

        System.out.println(
            "[COORDINADOR] Agente finalizado: "
            + getLocalName()
        );
    }
}
