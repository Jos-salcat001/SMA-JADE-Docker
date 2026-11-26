package servicios;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ServicioPronosticoTest {

    @Test
    void escenarioBase() {

        assertEquals(
            120,
            ServicioPronostico.calcular(
                new int[]{100, 120, 140}
            )
        );
    }

    @Test
    void redondeoSuperior() {

        assertEquals(
            102,
            ServicioPronostico.calcular(
                new int[]{100, 101, 104}
            )
        );
    }

    @Test
    void demandaNula() {

        assertEquals(
            0,
            ServicioPronostico.calcular(
                new int[]{0, 0, 0}
            )
        );
    }

    @Test
    void ventasVacias() {

        assertThrows(
            IllegalArgumentException.class,
            () -> ServicioPronostico.calcular(
                new int[]{}
            )
        );
    }

    @Test
    void ventasNegativas() {

        assertThrows(
            IllegalArgumentException.class,
            () -> ServicioPronostico.calcular(
                new int[]{100, -10, 120}
            )
        );
    }
}
