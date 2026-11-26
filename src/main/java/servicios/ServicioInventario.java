package servicios;

public final class ServicioInventario {

    private ServicioInventario() {
    }

    public static int calcularReposicion(
            int demanda,
            int stockActual,
            int stockSeguridad) {

        if (demanda < 0 ||
                stockActual < 0 ||
                stockSeguridad < 0) {

            throw new IllegalArgumentException(
                "Los valores no pueden ser negativos"
            );
        }

        long reposicion =
            (long) demanda
            + stockSeguridad
            - stockActual;

        return Math.toIntExact(
            Math.max(0L, reposicion)
        );
    }
}
