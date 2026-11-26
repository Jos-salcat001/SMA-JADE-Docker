package servicios;

import modelos.Proveedor;
import modelos.ResultadoAbastecimiento;
import modelos.ResultadoAbastecimiento.Estado;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ServicioAbastecimientoTest {

    private final Proveedor[] proveedores =
        ServicioAbastecimiento.proveedoresPorDefecto();

    @Test
    void escenarioBase() {

        ResultadoAbastecimiento r =
            ServicioAbastecimiento.evaluar(
                "PROD-001", 70, proveedores
            );

        assertEquals(
            Estado.PROVEEDOR_SELECCIONADO,
            r.getEstado()
        );

        assertEquals(
            "Proveedor C",
            r.getProveedor().getNombre()
        );

        assertEquals(70, r.getCantidad());

        assertEquals(
            1120.00, r.getCostoTotal(), 0.001
        );

        assertEquals(
            0.9375, r.getPuntaje(), 0.0001
        );

        assertEquals(
            1, r.getProveedor().getPlazo()
        );
    }

    @Test
    void noRequiereCompra() {

        ResultadoAbastecimiento r =
            ServicioAbastecimiento.evaluar(
                "PROD-001", 0, proveedores
            );

        assertEquals(
            Estado.NO_REQUIERE_COMPRA,
            r.getEstado()
        );

        assertFalse(r.tieneProveedor());
    }

    @Test
    void disponibilidadParcial() {

        ResultadoAbastecimiento r =
            ServicioAbastecimiento.evaluar(
                "PROD-001", 100, proveedores
            );

        assertEquals(
            "Proveedor C",
            r.getProveedor().getNombre()
        );

        assertEquals(
            0.96875, r.getPuntaje(), 0.0001
        );
    }

    @Test
    void unicoProveedorElegible() {

        ResultadoAbastecimiento r =
            ServicioAbastecimiento.evaluar(
                "PROD-001", 180, proveedores
            );

        assertEquals(
            "Proveedor C",
            r.getProveedor().getNombre()
        );

        assertEquals(
            1.0, r.getPuntaje(), 0.0001
        );
    }

    @Test
    void disponibilidadLimite() {

        ResultadoAbastecimiento r =
            ServicioAbastecimiento.evaluar(
                "PROD-001", 200, proveedores
            );

        assertEquals(
            Estado.PROVEEDOR_SELECCIONADO,
            r.getEstado()
        );

        assertEquals(
            3200.00, r.getCostoTotal(), 0.001
        );
    }

    @Test
    void sinProveedorElegible() {

        ResultadoAbastecimiento r =
            ServicioAbastecimiento.evaluar(
                "PROD-001", 201, proveedores
            );

        assertEquals(
            Estado.SIN_PROVEEDOR_ELEGIBLE,
            r.getEstado()
        );

        assertFalse(r.tieneProveedor());
    }

    @Test
    void catalogoVacio() {

        ResultadoAbastecimiento r =
            ServicioAbastecimiento.evaluar(
                "PROD-001", 70,
                new Proveedor[]{}
            );

        assertEquals(
            Estado.SIN_PROVEEDOR_ELEGIBLE,
            r.getEstado()
        );
    }

    @Test
    void cantidadInvalida() {

        assertThrows(
            IllegalArgumentException.class,
            () -> ServicioAbastecimiento.evaluar(
                "PROD-001", -10, proveedores
            )
        );
    }
}
