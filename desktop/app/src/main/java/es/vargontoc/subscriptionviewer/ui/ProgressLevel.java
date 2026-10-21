package es.vargontoc.subscriptionviewer.ui;

/**
 * Nivel de color de una barra de progreso según su porcentaje de consumo.
 *
 * <p>La clasificación se realiza sobre el porcentaje recibido, sin redondear.
 * Solo el valor {@code 100.0} se clasifica como {@link #NEGRO}; por tanto,
 * cualquier valor finito entre 90 y 100 que sea menor que 100 es {@link #ROJO}.</p>
 */
public enum ProgressLevel {
    VERDE,
    AMARILLO,
    NARANJA,
    ROJO,
    NEGRO;

    /**
     * Clasifica un porcentaje dentro del rango permitido [0, 100].
     *
     * @param percentage porcentaje sin redondear
     * @return nivel de color correspondiente
     * @throws IllegalArgumentException si el porcentaje no es finito o está
     *                                  fuera del rango [0, 100]
     */
    public static ProgressLevel of(double percentage) {
        if (!Double.isFinite(percentage) || percentage < 0.0 || percentage > 100.0) {
            throw new IllegalArgumentException("Percentage must be finite and between 0 and 100");
        }
        if (percentage == 100.0) {
            return NEGRO;
        }
        if (percentage <= 50.0) {
            return VERDE;
        }
        if (percentage <= 75.0) {
            return AMARILLO;
        }
        if (percentage <= 90.0) {
            return NARANJA;
        }
        return ROJO;
    }
}
