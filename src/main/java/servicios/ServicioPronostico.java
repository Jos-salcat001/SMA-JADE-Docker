package servicios;

public final class ServicioPronostico {

    private ServicioPronostico() {
    }

    public static int calcular(int[] ventas) {

        if (ventas == null || ventas.length == 0) {

            throw new IllegalArgumentException(
                "No existen ventas historicas"
            );
        }

        long suma = 0;

        for (int venta : ventas) {

            if (venta < 0) {

                throw new IllegalArgumentException(
                    "Venta historica invalida"
                );
            }

            suma += venta;
        }

        return (int) Math.ceil(
            (double) suma / ventas.length
        );
    }
}
