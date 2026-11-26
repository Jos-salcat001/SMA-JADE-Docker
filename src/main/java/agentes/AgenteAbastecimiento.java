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

import modelos.Proveedor;
import modelos.ResultadoAbastecimiento;

import servicios.ServicioAbastecimiento;

import java.util.Locale;
import java.util.UUID;

public class AgenteAbastecimiento extends Agent {

    private static final String ONTOLOGIA =
        "gestion-abastecimiento";

    private static final String ONTOLOGIA_INVENTARIO =
        "gestion-inventario";

    private static final String PRODUCTO_DEFAULT =
        "PROD-001";

    private static final long TIMEOUT = 10000;

    private AID agenteInventario;

    private String idSolicitud;

    private long tiempoSolicitud;

    private boolean solicitudPendiente = false;

    private final Proveedor[] proveedores =
        ServicioAbastecimiento.proveedoresPorDefecto();

    @Override
    protected void setup() {

        System.out.println(
            "[ABASTECIMIENTO] Agente iniciado: "
            + getAID().getName()
        );

        System.out.println(
            "[ABASTECIMIENTO] Version 1.2"
        );

        registrarServicio();

        // Atencion permanente de solicitudes dinamicas.

        iniciarAtencionSolicitudes();

        // Compatibilidad con la version anterior.
        // Puede desactivarse con el argumento dinamico.

        Object[] argumentos = getArguments();

        boolean modoAutomatico = true;

        if (argumentos != null
                && argumentos.length > 0) {

            modoAutomatico =
                !"dinamico".equalsIgnoreCase(
                    String.valueOf(argumentos[0])
                );
        }

        if (modoAutomatico) {

            iniciarConsultaAutomatica();

            System.out.println(
                "[ABASTECIMIENTO] Modo automatico activo"
            );

        } else {

            System.out.println(
                "[ABASTECIMIENTO] Modo dinamico activo"
            );
        }
    }

    // ==================================================
    // REGISTRO DEL SERVICIO EN DF
    // ==================================================

    private void registrarServicio() {

        DFAgentDescription descripcion =
            new DFAgentDescription();

        descripcion.setName(getAID());

        ServiceDescription servicio =
            new ServiceDescription();

        servicio.setType(ONTOLOGIA);

        servicio.setName(
            "servicio-abastecimiento"
        );

        descripcion.addServices(servicio);

        try {

            DFService.register(this, descripcion);

            System.out.println(
                "[ABASTECIMIENTO] Servicio registrado en DF"
            );

        } catch (FIPAException e) {

            e.printStackTrace();
        }
    }

    // ==================================================
    // ATENCION DE SOLICITUDES DINAMICAS
    // ==================================================

    private void iniciarAtencionSolicitudes() {

        addBehaviour(new CyclicBehaviour(this) {

            @Override
            public void action() {

                MessageTemplate filtro =
                    MessageTemplate.and(

                        MessageTemplate.MatchPerformative(
                            ACLMessage.REQUEST
                        ),

                        MessageTemplate.MatchOntology(
                            ONTOLOGIA
                        )
                    );

                ACLMessage solicitud = receive(filtro);

                if (solicitud == null) {

                    block();

                    return;
                }

                procesarSolicitudDinamica(solicitud);
            }
        });
    }

    private void procesarSolicitudDinamica(
            ACLMessage solicitud) {

        ACLMessage respuesta =
            solicitud.createReply();

        try {

            String contenido =
                solicitud.getContent();

            if (contenido == null) {

                throw new IllegalArgumentException(
                    "Contenido obligatorio"
                );
            }

            String[] datos =
                contenido.split(";", -1);

            if (datos.length != 2) {

                throw new IllegalArgumentException(
                    "Formato esperado: producto;cantidad"
                );
            }

            String producto = datos[0].trim();

            int cantidad =
                Integer.parseInt(datos[1].trim());

            System.out.println(
                "[ABASTECIMIENTO] Solicitud recibida de: "
                + solicitud.getSender().getName()
            );

            System.out.println(
                "[ABASTECIMIENTO] Conversacion: "
                + solicitud.getConversationId()
            );

            ResultadoAbastecimiento resultado =
                ServicioAbastecimiento.evaluar(
                    producto,
                    cantidad,
                    proveedores
                );

            mostrarResultado(resultado);

            respuesta.setPerformative(
                ACLMessage.INFORM
            );

            respuesta.setContent(
                serializarResultado(resultado)
            );

        } catch (IllegalArgumentException e) {

            respuesta.setPerformative(
                ACLMessage.REFUSE
            );

            respuesta.setContent(
                "SOLICITUD_INVALIDA;"
                + e.getMessage()
            );

        } catch (Exception e) {

            respuesta.setPerformative(
                ACLMessage.FAILURE
            );

            respuesta.setContent(
                "ERROR_ABASTECIMIENTO"
            );

            e.printStackTrace();
        }

        send(respuesta);

        System.out.println(
            "[ABASTECIMIENTO] Respuesta ACL enviada: "
            + respuesta.getContent()
        );
    }

    // ==================================================
    // COMPATIBILIDAD CON EL MODO AUTOMATICO
    // ==================================================

    private void iniciarConsultaAutomatica() {

        addBehaviour(new TickerBehaviour(this, 2000) {

            @Override
            protected void onTick() {

                if (!solicitudPendiente) {

                    agenteInventario =
                        buscarAgenteInventario();

                    if (agenteInventario != null) {

                        solicitarReposicion();
                    }

                    return;
                }

                MessageTemplate filtro =
                    MessageTemplate.and(

                        MessageTemplate.MatchSender(
                            agenteInventario
                        ),

                        MessageTemplate.and(

                            MessageTemplate.MatchOntology(
                                ONTOLOGIA_INVENTARIO
                            ),

                            MessageTemplate.MatchInReplyTo(
                                idSolicitud
                            )
                        )
                    );

                ACLMessage respuesta =
                    receive(filtro);

                if (respuesta != null) {

                    solicitudPendiente = false;

                    if ("PENDIENTE_CALCULO".equals(
                            respuesta.getContent())) {

                        System.out.println(
                            "[ABASTECIMIENTO] "
                            + "Esperando calculo de inventario"
                        );

                        return;
                    }

                    if (respuesta.getPerformative()
                            == ACLMessage.INFORM) {

                        if (procesarReposicion(
                                respuesta.getContent())) {

                            // Solo detiene el modo automatico.
                            // El CyclicBehaviour sigue activo.

                            stop();
                        }

                    } else {

                        System.err.println(
                            "[ABASTECIMIENTO] "
                            + "Solicitud rechazada: "
                            + respuesta.getContent()
                        );

                        stop();
                    }

                } else if (

                    System.currentTimeMillis()
                    - tiempoSolicitud > TIMEOUT) {

                    solicitudPendiente = false;

                    System.out.println(
                        "[ABASTECIMIENTO] "
                        + "Tiempo de espera agotado. "
                        + "Reintentando."
                    );
                }
            }
        });
    }

    // ==================================================
    // DESCUBRIMIENTO DEL AGENTE INVENTARIO
    // ==================================================

    private AID buscarAgenteInventario() {

        DFAgentDescription plantilla =
            new DFAgentDescription();

        ServiceDescription servicio =
            new ServiceDescription();

        servicio.setType(
            ONTOLOGIA_INVENTARIO
        );

        plantilla.addServices(servicio);

        try {

            DFAgentDescription[] resultados =
                DFService.search(this, plantilla);

            if (resultados.length > 0) {

                AID agente =
                    resultados[0].getName();

                System.out.println(
                    "[ABASTECIMIENTO] Agente encontrado: "
                    + agente.getName()
                );

                return agente;
            }

        } catch (FIPAException e) {

            e.printStackTrace();
        }

        return null;
    }

    // ==================================================
    // SOLICITUD ACL AL AGENTE INVENTARIO
    // ==================================================

    private void solicitarReposicion() {

        ACLMessage solicitud =
            new ACLMessage(ACLMessage.REQUEST);

        solicitud.addReceiver(agenteInventario);

        solicitud.setOntology(
            ONTOLOGIA_INVENTARIO
        );

        solicitud.setConversationId(
            "consulta-reposicion"
        );

        idSolicitud =
            UUID.randomUUID().toString();

        solicitud.setReplyWith(idSolicitud);

        solicitud.setContent(PRODUCTO_DEFAULT);

        tiempoSolicitud =
            System.currentTimeMillis();

        solicitudPendiente = true;

        send(solicitud);

        System.out.println(
            "[ABASTECIMIENTO] ACL REQUEST enviado a: "
            + agenteInventario.getName()
        );
    }

    // ==================================================
    // PROCESAMIENTO DE REPOSICION AUTOMATICA
    // ==================================================

    private boolean procesarReposicion(
            String contenido) {

        try {

            if (contenido == null) {
                return false;
            }

            String[] datos =
                contenido.split(";", -1);

            if (datos.length != 2
                    || !PRODUCTO_DEFAULT.equals(datos[0])) {

                System.err.println(
                    "[ABASTECIMIENTO] Respuesta invalida"
                );

                return false;
            }

            int cantidad =
                Integer.parseInt(datos[1]);

            ResultadoAbastecimiento resultado =
                ServicioAbastecimiento.evaluar(
                    datos[0],
                    cantidad,
                    proveedores
                );

            System.out.println(
                "[ABASTECIMIENTO] ACL INFORM recibido"
            );

            mostrarResultado(resultado);

            return true;

        } catch (IllegalArgumentException e) {

            System.err.println(
                "[ABASTECIMIENTO] Datos incorrectos: "
                + e.getMessage()
            );

            return false;
        }
    }

    // ==================================================
    // SERIALIZACION DEL RESULTADO PARA MENSAJES ACL
    // ==================================================

    private String serializarResultado(
            ResultadoAbastecimiento resultado) {

        Proveedor proveedor =
            resultado.getProveedor();

        String nombre = "";

        double precio = 0.0;

        int plazo = 0;

        if (proveedor != null) {

            nombre = proveedor.getNombre();

            precio = proveedor.getPrecio();

            plazo = proveedor.getPlazo();
        }

        return String.format(

            Locale.US,

            "%s;%s;%d;%s;%.2f;%.2f;%d;%.4f",

            resultado.getEstado().name(),

            resultado.getProducto(),

            resultado.getCantidad(),

            nombre,

            precio,

            resultado.getCostoTotal(),

            plazo,

            resultado.getPuntaje()
        );
    }

    // ==================================================
    // PRESENTACION DEL RESULTADO EN CONSOLA
    // ==================================================

    private void mostrarResultado(
            ResultadoAbastecimiento resultado) {

        System.out.println(
            "======================================"
        );

        System.out.println(
            " RECOMENDACION DE ABASTECIMIENTO"
        );

        System.out.println(
            "======================================"
        );

        System.out.println(
            "Estado: " + resultado.getEstado()
        );

        System.out.println(
            "Producto: " + resultado.getProducto()
        );

        System.out.println(
            "Cantidad: "
            + resultado.getCantidad()
            + " unidades"
        );

        if (resultado.tieneProveedor()) {

            Proveedor proveedor =
                resultado.getProveedor();

            System.out.println(
                "Proveedor: "
                + proveedor.getNombre()
            );

            System.out.printf(

                Locale.US,

                "Precio unitario: S/ %.2f%n",

                proveedor.getPrecio()
            );

            System.out.printf(

                Locale.US,

                "Costo total: S/ %.2f%n",

                resultado.getCostoTotal()
            );

            System.out.println(
                "Plazo de entrega: "
                + proveedor.getPlazo()
                + " dias"
            );

            System.out.printf(

                Locale.US,

                "Puntaje obtenido: %.4f%n",

                resultado.getPuntaje()
            );

        } else {

            switch (resultado.getEstado()) {

                case NO_REQUIERE_COMPRA:

                    System.out.println(
                        "No se requiere compra"
                    );

                    break;

                case SIN_PROVEEDOR_ELEGIBLE:

                    System.out.println(
                        "No existen proveedores elegibles"
                    );

                    break;

                default:

                    break;
            }
        }

        System.out.println(
            "======================================"
        );
    }

    // ==================================================
    // FINALIZACION DEL AGENTE
    // ==================================================

    @Override
    protected void takeDown() {

        try {

            DFService.deregister(this);

        } catch (FIPAException e) {

            e.printStackTrace();
        }

        System.out.println(
            "[ABASTECIMIENTO] Agente finalizado: "
            + getLocalName()
        );
    }
}