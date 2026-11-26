package servicios;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ServicioInventarioTest {

    @Test
    void escenarioBase() {
        assertEquals(70,
            ServicioInventario.calcularReposicion(
                120, 70, 20));
    }

    @Test
    void stockSuficiente() {
        assertEquals(0,
            ServicioInventario.calcularReposicion(
                80, 150, 20));
    }

    @Test
    void demandaElevada() {
        assertEquals(200,
            ServicioInventario.calcularReposicion(
                250, 70, 20));
    }

    @Test
    void agotamientoStock() {
        assertEquals(140,
            ServicioInventario.calcularReposicion(
                120, 0, 20));
    }

    @Test
    void equilibrioInventario() {
        assertEquals(0,
            ServicioInventario.calcularReposicion(
                100, 120, 20));
    }

    @Test
    void reposicionMinima() {
        assertEquals(1,
            ServicioInventario.calcularReposicion(
                100, 119, 20));
    }

    @Test
    void demandaNula() {
        assertEquals(10,
            ServicioInventario.calcularReposicion(
                0, 10, 20));
    }

    @Test
    void valoresInvalidos() {

        assertThrows(
            IllegalArgumentException.class,
            () -> ServicioInventario.calcularReposicion(
                120, -70, 20)
        );
    }
}
