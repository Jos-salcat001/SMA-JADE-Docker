package modelos;

import java.util.UUID;

public final class Escenario {

    private final String id;
    private final String producto;
    private final int[] ventasHistoricas;
    private final int stockActual;
    private final int stockSeguridad;

    public Escenario(
            String producto,
            int[] ventasHistoricas,
            int stockActual,
            int stockSeguridad) {

        if (producto == null ||
                producto.trim().isEmpty()) {

            throw new IllegalArgumentException(
                "El producto es obligatorio"
            );
        }

        if (ventasHistoricas == null ||
                ventasHistoricas.length == 0) {

            throw new IllegalArgumentException(
                "Se requieren ventas historicas"
            );
        }

        for (int venta : ventasHistoricas) {

            if (venta < 0) {

                throw new IllegalArgumentException(
                    "Las ventas no pueden ser negativas"
                );
            }
        }

        if (stockActual < 0 || stockSeguridad < 0) {

            throw new IllegalArgumentException(
                "El stock no puede ser negativo"
            );
        }

        this.id = UUID.randomUUID().toString();
        this.producto = producto.trim();
        this.ventasHistoricas = ventasHistoricas.clone();
        this.stockActual = stockActual;
        this.stockSeguridad = stockSeguridad;
    }

    public String getId() {
        return id;
    }

    public String getProducto() {
        return producto;
    }

    public int[] getVentasHistoricas() {
        return ventasHistoricas.clone();
    }

    public int getStockActual() {
        return stockActual;
    }

    public int getStockSeguridad() {
        return stockSeguridad;
    }
}
