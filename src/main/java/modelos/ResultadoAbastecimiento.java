package modelos;

public final class ResultadoAbastecimiento {

    public enum Estado {

        NO_REQUIERE_COMPRA,
        PROVEEDOR_SELECCIONADO,
        SIN_PROVEEDOR_ELEGIBLE
    }

    private final Estado estado;
    private final String producto;
    private final int cantidad;
    private final Proveedor proveedor;
    private final double puntaje;

    public ResultadoAbastecimiento(
            Estado estado,
            String producto,
            int cantidad,
            Proveedor proveedor,
            double puntaje) {

        this.estado = estado;
        this.producto = producto;
        this.cantidad = cantidad;
        this.proveedor = proveedor;
        this.puntaje = puntaje;
    }

    public Estado getEstado() {
        return estado;
    }

    public String getProducto() {
        return producto;
    }

    public int getCantidad() {
        return cantidad;
    }

    public Proveedor getProveedor() {
        return proveedor;
    }

    public double getPuntaje() {
        return puntaje;
    }

    public double getCostoTotal() {

        if (proveedor == null) {
            return 0.0;
        }

        return cantidad * proveedor.getPrecio();
    }

    public boolean tieneProveedor() {
        return proveedor != null;
    }
}
