package agentes;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;

import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import servicios.ServicioPronostico;

public class AgenteDemanda extends Agent {

    private static final String ONTOLOGIA =
        "gestion-demanda";

    private static final String PRODUCTO_DEFAULT =
        "PROD-001";

    private static final int[] VENTAS_DEFAULT = {
        100, 120, 140
    };

    @Override
    protected void setup() {

        System.out.println(
            "[DEMANDA] Agente iniciado: "
            + getAID().getName()
        );

        System.out.println(
            "[DEMANDA] Version 1.2"
        );

        if (!registrarServicio()) {

            doDelete();
            return;
        }

        addBehaviour(new CyclicBehaviour() {

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

                ACLMessage mensaje = receive(filtro);

                if (mensaje == null) {

                    block();
                    return;
                }

                procesarSolicitud(mensaje);
            }
        });

        System.out.println(
            "[DEMANDA] Disponible para nuevos escenarios"
        );
    }

    private void procesarSolicitud(
            ACLMessage mensaje) {

        ACLMessage respuesta = mensaje.createReply();

        respuesta.setOntology(ONTOLOGIA);

        System.out.println(
            "[DEMANDA] Solicitud recibida de: "
            + mensaje.getSender().getName()
        );

        System.out.println(
            "[DEMANDA] Conversacion: "
            + mensaje.getConversationId()
        );

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

            String producto;
            int[] ventas;

            // Compatibilidad con version 1.1

            if (datos.length == 1) {

                producto = datos[0].trim();

                if (!PRODUCTO_DEFAULT.equals(producto)) {

                    respuesta.setPerformative(
                        ACLMessage.REFUSE
                    );

                    respuesta.setContent(
                        "PRODUCTO_NO_DISPONIBLE"
                    );

                    send(respuesta);
                    return;
                }

                ventas = VENTAS_DEFAULT.clone();

            // Solicitud parametrizada version 1.2

            } else if (datos.length == 2) {

                producto = datos[0].trim();

                if (producto.isEmpty()) {

                    throw new IllegalArgumentException(
                        "PRODUCTO_OBLIGATORIO"
                    );
                }

                ventas = convertirVentas(datos[1]);

            } else {

                throw new IllegalArgumentException(
                    "FORMATO_INVALIDO"
                );
            }

            // Delegar el calculo al servicio de negocio

            int pronostico =
                ServicioPronostico.calcular(ventas);

            respuesta.setPerformative(
                ACLMessage.INFORM
            );

            respuesta.setContent(
                producto + ";" + pronostico
            );

            System.out.println(
                "[DEMANDA] Producto: " + producto
            );

            System.out.println(
                "[DEMANDA] Ventas historicas: "
                + java.util.Arrays.toString(ventas)
            );

            System.out.println(
                "[DEMANDA] Pronostico: "
                + pronostico + " unidades"
            );

        } catch (IllegalArgumentException e) {

            respuesta.setPerformative(
                ACLMessage.REFUSE
            );

            respuesta.setContent(
                "DATOS_INVALIDOS: " + e.getMessage()
            );

            System.err.println(
                "[DEMANDA] Solicitud rechazada: "
                + e.getMessage()
            );

        } catch (Exception e) {

            respuesta.setPerformative(
                ACLMessage.FAILURE
            );

            respuesta.setContent(
                "ERROR_CALCULO_PRONOSTICO"
            );

            System.err.println(
                "[DEMANDA] Error: " + e.getMessage()
            );
        }

        send(respuesta);

        System.out.println(
            "[DEMANDA] Respuesta enviada: "
            + respuesta.getContent()
        );
    }

    private int[] convertirVentas(String contenido) {

        if (contenido == null
                || contenido.trim().isEmpty()) {

            throw new IllegalArgumentException(
                "VENTAS_HISTORICAS_OBLIGATORIAS"
            );
        }

        String[] valores =
            contenido.split(",", -1);

        int[] ventas = new int[valores.length];

        for (int i = 0; i < valores.length; i++) {

            ventas[i] =
                Integer.parseInt(valores[i].trim());

            if (ventas[i] < 0) {

                throw new IllegalArgumentException(
                    "VENTAS_NEGATIVAS"
                );
            }
        }

        return ventas;
    }

    private boolean registrarServicio() {

        DFAgentDescription descripcion =
            new DFAgentDescription();

        descripcion.setName(getAID());

        ServiceDescription servicio =
            new ServiceDescription();

        servicio.setType("pronostico-demanda");

        servicio.setName("servicio-demanda");

        descripcion.addServices(servicio);

        try {

            DFService.register(this, descripcion);

            System.out.println(
                "[DEMANDA] Servicio registrado en DF"
            );

            return true;

        } catch (FIPAException e) {

            System.err.println(
                "[DEMANDA] Error de registro en DF"
            );

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

        System.out.println(
            "[DEMANDA] Agente finalizado: "
            + getLocalName()
        );
    }
}

