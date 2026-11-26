package servicios;

import modelos.Proveedor;
import modelos.ResultadoAbastecimiento;
import modelos.ResultadoAbastecimiento.Estado;

public final class ServicioAbastecimiento {

    private static final double PESO_PRECIO = 0.50;
    private static final double PESO_PLAZO = 0.30;
    private static final double PESO_DISPONIBILIDAD = 0.20;

    private ServicioAbastecimiento() {
    }

    public static Proveedor[] proveedoresPorDefecto() {

        return new Proveedor[] {

            new Proveedor("Proveedor A", 15.00, 150, 3),

            new Proveedor("Proveedor B", 14.00, 80, 5),

            new Proveedor("Proveedor C", 16.00, 200, 1)
        };
    }

    public static ResultadoAbastecimiento evaluar(
            String producto,
            int cantidad,
            Proveedor[] proveedores) {

        if (producto == null || producto.trim().isEmpty()) {
            throw new IllegalArgumentException(
                "Producto obligatorio"
            );
        }

        if (cantidad < 0) {
            throw new IllegalArgumentException(
                "Cantidad invalida"
            );
        }

        if (proveedores == null) {
            throw new IllegalArgumentException(
                "Catalogo de proveedores invalido"
            );
        }

        if (cantidad == 0) {

            return new ResultadoAbastecimiento(
                Estado.NO_REQUIERE_COMPRA,
                producto,
                cantidad,
                null,
                0.0
            );
        }

        double precioMinimo = Double.MAX_VALUE;
        int plazoMinimo = Integer.MAX_VALUE;
        int disponibilidadMaxima = 0;

        // Determinar valores de normalizacion
        // utilizando proveedores elegibles.

        for (Proveedor p : proveedores) {

            if (p == null) {
                throw new IllegalArgumentException(
                    "Proveedor invalido"
                );
            }

            if (p.getDisponibilidad() >= cantidad) {

                precioMinimo = Math.min(
                    precioMinimo,
                    p.getPrecio()
                );

                plazoMinimo = Math.min(
                    plazoMinimo,
                    p.getPlazo()
                );

                disponibilidadMaxima = Math.max(
                    disponibilidadMaxima,
                    p.getDisponibilidad()
                );
            }
        }

        Proveedor seleccionado = null;
        double mejorPuntaje = -1.0;

        // Evaluacion multicriterio.

        for (Proveedor p : proveedores) {

            if (p.getDisponibilidad() < cantidad) {
                continue;
            }

            double criterioPrecio =
                precioMinimo / p.getPrecio();

            double criterioPlazo =
                (double) plazoMinimo / p.getPlazo();

            double criterioDisponibilidad =
                (double) p.getDisponibilidad()
                / disponibilidadMaxima;

            double puntaje =
                PESO_PRECIO * criterioPrecio
                + PESO_PLAZO * criterioPlazo
                + PESO_DISPONIBILIDAD
                    * criterioDisponibilidad;

            if (puntaje > mejorPuntaje) {

                mejorPuntaje = puntaje;
                seleccionado = p;
            }
        }

        if (seleccionado == null) {

            return new ResultadoAbastecimiento(
                Estado.SIN_PROVEEDOR_ELEGIBLE,
                producto,
                cantidad,
                null,
                0.0
            );
        }

        return new ResultadoAbastecimiento(
            Estado.PROVEEDOR_SELECCIONADO,
            producto,
            cantidad,
            seleccionado,
            mejorPuntaje
        );
    }
}
