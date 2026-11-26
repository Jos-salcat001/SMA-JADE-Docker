package modelos;

public final class Proveedor {

    private final String nombre;
    private final double precio;
    private final int disponibilidad;
    private final int plazo;

    public Proveedor(
            String nombre,
            double precio,
            int disponibilidad,
            int plazo) {

        if (nombre == null || nombre.trim().isEmpty()) {
            throw new IllegalArgumentException(
                "Nombre de proveedor obligatorio"
            );
        }

        if (!Double.isFinite(precio) || precio <= 0) {
            throw new IllegalArgumentException(
                "Precio invalido"
            );
        }

        if (disponibilidad < 0 || plazo <= 0) {
            throw new IllegalArgumentException(
                "Disponibilidad o plazo invalido"
            );
        }

        this.nombre = nombre.trim();
        this.precio = precio;
        this.disponibilidad = disponibilidad;
        this.plazo = plazo;
    }

    public String getNombre() {
        return nombre;
    }

    public double getPrecio() {
        return precio;
    }

    public int getDisponibilidad() {
        return disponibilidad;
    }

    public int getPlazo() {
        return plazo;
    }
}
