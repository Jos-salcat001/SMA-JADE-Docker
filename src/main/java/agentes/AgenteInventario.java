package agentes;

import jade.core.Agent;
import jade.core.AID;

import jade.core.behaviours.TickerBehaviour;
import jade.core.behaviours.CyclicBehaviour;

import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAAgentManagement.SearchConstraints;

import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import servicios.ServicioInventario;

import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;

public class AgenteInventario extends Agent {

    private static final String ONTOLOGIA_INVENTARIO =
        "gestion-inventario";

    private static final String ONTOLOGIA_DEMANDA =
        "gestion-demanda";

    private static final String PRODUCTO_DEFAULT =
        "PROD-001";

    private static final String VENTAS_DEFAULT =
        "100,120,140";

    private static final int STOCK_DEFAULT = 70;

    private static final int SEGURIDAD_DEFAULT = 20;

    private static final long TIMEOUT = 15000L;

    // Configuracion de la plataforma remota P1

    private final String plataformaRemota = "P1";

    private final String direccionRemota =
        "http://jade-p1:7778/acc";

    // Compatibilidad con version 1.1

    private boolean reposicionCalculada = false;

    private boolean escenarioBasePendiente = false;

    private int cantidadReposicion;

    private AID agenteDemanda;

    // Solicitudes independientes por escenario

    private final Map<String, SolicitudPendiente>
        pendientes = new HashMap<>();

    // Contexto individual de cada solicitud

    private static class SolicitudPendiente {

        String id;
        String conversacion;
        String producto;
        String ventas;

        int stockActual;
        int stockSeguridad;

        long tiempoInicio;

        boolean escenarioBase;

        AID destinatarioDemanda;

        ACLMessage solicitudOriginal;
    }

    @Override
    protected void setup() {

        System.out.println(
            "[INVENTARIO] Agente iniciado: "
            + getAID().getName()
        );

        System.out.println(
            "[INVENTARIO] Version 1.2"
        );

        if (!registrarServicio()) {

            doDelete();
            return;
        }

        // Comportamiento 1:
        // Recepcion de solicitudes de inventario

        addBehaviour(new CyclicBehaviour() {

            @Override
            public void action() {

                MessageTemplate filtro =
                    MessageTemplate.and(

                        MessageTemplate.MatchPerformative(
                            ACLMessage.REQUEST
                        ),

                        MessageTemplate.MatchOntology(
                            ONTOLOGIA_INVENTARIO
                        )
                    );

                ACLMessage mensaje = receive(filtro);

                if (mensaje == null) {

                    block();
                    return;
                }

                procesarSolicitudInventario(mensaje);
            }
        });

        // Comportamiento 2:
        // Recepcion de respuestas del agente demanda

        addBehaviour(new CyclicBehaviour() {

            @Override
            public void action() {

                MessageTemplate filtro =
                    MessageTemplate.MatchOntology(
                        ONTOLOGIA_DEMANDA
                    );

                ACLMessage mensaje = receive(filtro);

                if (mensaje == null) {

                    block();
                    return;
                }

                procesarRespuestaDemanda(mensaje);
            }
        });

        // Comportamiento 3:
        // Descubrimiento y escenario base

        addBehaviour(new TickerBehaviour(this, 2000) {

            @Override
            protected void onTick() {

                if (agenteDemanda == null) {

                    agenteDemanda =
                        buscarAgenteDemanda();
                }

                if (agenteDemanda != null
                        && !reposicionCalculada
                        && !escenarioBasePendiente) {

                    SolicitudPendiente contexto =
                        new SolicitudPendiente();

                    contexto.producto =
                        PRODUCTO_DEFAULT;

                    contexto.ventas =
                        VENTAS_DEFAULT;

                    contexto.stockActual =
                        STOCK_DEFAULT;

                    contexto.stockSeguridad =
                        SEGURIDAD_DEFAULT;

                    contexto.escenarioBase = true;

                    escenarioBasePendiente = true;

                    solicitarPronostico(contexto);
                }
            }
        });

        // Comportamiento 4:
        // Control de solicitudes vencidas

        addBehaviour(new TickerBehaviour(this, 1000) {

            @Override
            protected void onTick() {

                verificarTiemposEspera();
            }
        });

        System.out.println(
            "[INVENTARIO] Disponible para escenarios"
        );
    }

    private void procesarSolicitudInventario(
            ACLMessage mensaje) {

        try {

            String contenido = mensaje.getContent();

            if (contenido == null
                    || contenido.trim().isEmpty()) {

                throw new IllegalArgumentException(
                    "SOLICITUD_VACIA"
                );
            }

            String[] datos =
                contenido.split(";", -1);

            // Protocolo anterior version 1.1

            if (datos.length == 1) {

                responderConsultaAnterior(
                    mensaje,
                    datos[0].trim()
                );

                return;
            }

            // Protocolo dinamico version 1.2

            if (datos.length != 4) {

                throw new IllegalArgumentException(
                    "FORMATO_INVALIDO"
                );
            }

            SolicitudPendiente contexto =
                new SolicitudPendiente();

            contexto.producto = datos[0].trim();

            contexto.ventas = datos[1].trim();

            contexto.stockActual =
                Integer.parseInt(datos[2].trim());

            contexto.stockSeguridad =
                Integer.parseInt(datos[3].trim());

            contexto.solicitudOriginal = mensaje;

            contexto.escenarioBase = false;

            validarContexto(contexto);

            if (agenteDemanda == null) {

                responder(
                    mensaje,
                    ACLMessage.REFUSE,
                    "SERVICIO_DEMANDA_NO_DISPONIBLE"
                );

                return;
            }

            solicitarPronostico(contexto);

        } catch (IllegalArgumentException e) {

            responder(
                mensaje,
                ACLMessage.REFUSE,
                "DATOS_INVALIDOS: " + e.getMessage()
            );
        }
    }

    private void validarContexto(
            SolicitudPendiente contexto) {

        if (contexto.producto.isEmpty()) {

            throw new IllegalArgumentException(
                "PRODUCTO_OBLIGATORIO"
            );
        }

        if (contexto.ventas.isEmpty()) {

            throw new IllegalArgumentException(
                "VENTAS_HISTORICAS_OBLIGATORIAS"
            );
        }

        String[] valores =
            contexto.ventas.split(",", -1);

        for (String valor : valores) {

            int venta =
                Integer.parseInt(valor.trim());

            if (venta < 0) {

                throw new IllegalArgumentException(
                    "VENTAS_NEGATIVAS"
                );
            }
        }

        if (contexto.stockActual < 0
                || contexto.stockSeguridad < 0) {

            throw new IllegalArgumentException(
                "STOCK_NEGATIVO"
            );
        }
    }

    private void responderConsultaAnterior(
            ACLMessage mensaje,
            String producto) {

        if (!PRODUCTO_DEFAULT.equals(producto)) {

            responder(
                mensaje,
                ACLMessage.REFUSE,
                "PRODUCTO_NO_DISPONIBLE"
            );

        } else if (!reposicionCalculada) {

            responder(
                mensaje,
                ACLMessage.REFUSE,
                "PENDIENTE_CALCULO"
            );

        } else {

            responder(
                mensaje,
                ACLMessage.INFORM,
                producto + ";" + cantidadReposicion
            );
        }
    }

    private void solicitarPronostico(
            SolicitudPendiente contexto) {

        contexto.id =
            UUID.randomUUID().toString();

        contexto.tiempoInicio =
            System.currentTimeMillis();

        contexto.destinatarioDemanda =
            agenteDemanda;

        String conversacion = null;

        if (contexto.solicitudOriginal != null) {

            conversacion =
                contexto.solicitudOriginal
                    .getConversationId();
        }

        if (conversacion == null
                || conversacion.trim().isEmpty()) {

            conversacion =
                "escenario-" + contexto.id;
        }

        contexto.conversacion = conversacion;

        ACLMessage solicitud =
            new ACLMessage(ACLMessage.REQUEST);

        solicitud.addReceiver(
            contexto.destinatarioDemanda
        );

        solicitud.setOntology(
            ONTOLOGIA_DEMANDA
        );

        solicitud.setConversationId(
            contexto.conversacion
        );

        solicitud.setReplyWith(contexto.id);

        solicitud.setContent(
            contexto.producto
            + ";"
            + contexto.ventas
        );

        pendientes.put(
            contexto.id,
            contexto
        );

        send(solicitud);

        System.out.println(
            "[INVENTARIO] ACL REQUEST enviado a: "
            + contexto.destinatarioDemanda.getName()
        );

        System.out.println(
            "[INVENTARIO] Conversacion: "
            + contexto.conversacion
        );

        System.out.println(
            "[INVENTARIO] Solicitud: "
            + solicitud.getContent()
        );
    }

    private void procesarRespuestaDemanda(
            ACLMessage mensaje) {

        String id = mensaje.getInReplyTo();

        if (id == null) {

            return;
        }

        SolicitudPendiente contexto =
            pendientes.get(id);

        if (contexto == null) {

            System.out.println(
                "[INVENTARIO] Respuesta sin solicitud activa"
            );

            return;
        }

        // Verificar origen y correlacion

        if (!contexto.destinatarioDemanda.equals(
                mensaje.getSender())
                || !contexto.conversacion.equals(
                    mensaje.getConversationId())) {

            System.err.println(
                "[INVENTARIO] Respuesta no correlacionada"
            );

            return;
        }

        pendientes.remove(id);

        if (contexto.escenarioBase) {

            escenarioBasePendiente = false;
        }

        if (mensaje.getPerformative()
                != ACLMessage.INFORM) {

            informarError(
                contexto,
                "PRONOSTICO_NO_DISPONIBLE: "
                + mensaje.getContent()
            );

            return;
        }

        try {

            String contenido = mensaje.getContent();

            if (contenido == null) {

                throw new IllegalArgumentException(
                    "RESPUESTA_VACIA"
                );
            }

            String[] datos =
                contenido.split(";", -1);

            if (datos.length != 2
                    || !contexto.producto.equals(
                        datos[0])) {

                throw new IllegalArgumentException(
                    "RESPUESTA_INVALIDA"
                );
            }

            int demanda =
                Integer.parseInt(datos[1]);

            // Delegar el calculo al servicio validado

            int reposicion =
                ServicioInventario.calcularReposicion(
                    demanda,
                    contexto.stockActual,
                    contexto.stockSeguridad
                );

            mostrarResultado(
                contexto,
                demanda,
                reposicion
            );

            if (contexto.escenarioBase) {

                cantidadReposicion = reposicion;

                reposicionCalculada = true;

            } else {

                responder(
                    contexto.solicitudOriginal,
                    ACLMessage.INFORM,
                    contexto.producto
                    + ";" + demanda
                    + ";" + reposicion
                );
            }

        } catch (IllegalArgumentException
                | ArithmeticException e) {

            informarError(
                contexto,
                "ERROR_CALCULO_INVENTARIO: "
                + e.getMessage()
            );
        }
    }

    private void mostrarResultado(
            SolicitudPendiente contexto,
            int demanda,
            int reposicion) {

        System.out.println(
            "[INVENTARIO] ACL INFORM recibido"
        );

        System.out.println(
            "[INVENTARIO] Conversacion: "
            + contexto.conversacion
        );

        System.out.println(
            "[INVENTARIO] Producto: "
            + contexto.producto
        );

        System.out.println(
            "[INVENTARIO] Demanda recibida: "
            + demanda
        );

        System.out.println(
            "[INVENTARIO] Stock disponible: "
            + contexto.stockActual
        );

        System.out.println(
            "[INVENTARIO] Stock de seguridad: "
            + contexto.stockSeguridad
        );

        System.out.println(
            "[INVENTARIO] Reposicion necesaria: "
            + reposicion + " unidades"
        );

        if (reposicion > 0) {

            System.out.println(
                "[INVENTARIO] ALERTA: "
                + "Se requiere abastecimiento"
            );

        } else {

            System.out.println(
                "[INVENTARIO] Inventario suficiente"
            );
        }
    }

    private void verificarTiemposEspera() {

        long ahora = System.currentTimeMillis();

        Iterator<Map.Entry<String, SolicitudPendiente>>
            iterador =
                pendientes.entrySet().iterator();

        while (iterador.hasNext()) {

            SolicitudPendiente contexto =
                iterador.next().getValue();

            if (ahora - contexto.tiempoInicio
                    >= TIMEOUT) {

                iterador.remove();

                if (contexto.escenarioBase) {

                    escenarioBasePendiente = false;
                }

                agenteDemanda = null;

                informarError(
                    contexto,
                    "TIMEOUT_PRONOSTICO"
                );
            }
        }
    }

    private void informarError(
            SolicitudPendiente contexto,
            String error) {

        System.err.println(
            "[INVENTARIO] " + error
        );

        if (contexto.solicitudOriginal != null) {

            responder(
                contexto.solicitudOriginal,
                ACLMessage.FAILURE,
                error
            );
        }
    }

    private void responder(
            ACLMessage mensaje,
            int performativa,
            String contenido) {

        ACLMessage respuesta =
            mensaje.createReply();

        respuesta.setPerformative(performativa);

        respuesta.setOntology(
            ONTOLOGIA_INVENTARIO
        );

        respuesta.setContent(contenido);

        send(respuesta);

        System.out.println(
            "[INVENTARIO] Respuesta enviada: "
            + contenido
        );
    }

    private boolean registrarServicio() {

        DFAgentDescription descripcion =
            new DFAgentDescription();

        descripcion.setName(getAID());

        ServiceDescription servicio =
            new ServiceDescription();

        servicio.setType("gestion-inventario");

        servicio.setName("servicio-inventario");

        descripcion.addServices(servicio);

        try {

            DFService.register(this, descripcion);

            System.out.println(
                "[INVENTARIO] Servicio registrado en DF"
            );

            return true;

        } catch (FIPAException e) {

            e.printStackTrace();

            return false;
        }
    }

    private AID buscarAgenteDemanda() {

        DFAgentDescription plantilla =
            new DFAgentDescription();

        ServiceDescription servicio =
            new ServiceDescription();

        servicio.setType("pronostico-demanda");

        plantilla.addServices(servicio);

        // 1. Buscar en DF local

        try {

            DFAgentDescription[] resultados =
                DFService.search(this, plantilla);

            if (resultados.length > 0) {

                AID agente =
                    resultados[0].getName();

                System.out.println(
                    "[INVENTARIO] Agente encontrado "
                    + "en DF local: "
                    + agente.getName()
                );

                return agente;
            }

        } catch (FIPAException e) {

            System.err.println(
                "[INVENTARIO] Error DF local: "
                + e.getMessage()
            );
        }

        // 2. Identificar DF remoto

        AID dfRemoto = new AID(
            "df@" + plataformaRemota,
            AID.ISGUID
        );

        dfRemoto.addAddresses(direccionRemota);

        System.out.println(
            "[INVENTARIO] Consultando DF remoto: "
            + dfRemoto.getName()
        );

        System.out.println(
            "[INVENTARIO] Direccion HTTP-MTP: "
            + direccionRemota
        );

        SearchConstraints restricciones =
            new SearchConstraints();

        restricciones.setMaxResults(
            Long.valueOf(10)
        );

        // 3. Buscar servicio remoto

        try {

            DFAgentDescription[] resultadosRemotos =
                DFService.search(
                    this,
                    dfRemoto,
                    plantilla,
                    restricciones
                );

            if (resultadosRemotos.length > 0) {

                AID agente =
                    resultadosRemotos[0].getName();

                if (!agente.getAllAddresses().hasNext()) {

                    agente.addAddresses(
                        direccionRemota
                    );
                }

                System.out.println(
                    "[INVENTARIO] Servicio remoto encontrado"
                );

                System.out.println(
                    "[INVENTARIO] Agente remoto: "
                    + agente.getName()
                );

                return agente;
            }

        } catch (FIPAException e) {

            System.err.println(
                "[INVENTARIO] Error DF remoto: "
                + e.getMessage()
            );
        }

        return null;
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
            "[INVENTARIO] Agente finalizado: "
            + getLocalName()
        );
    }
}
