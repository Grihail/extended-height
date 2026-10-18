package io.github.extendedheight.api;

/** Клиентские эффекты у дна расширенного мира: туман, зона и количество частиц; неизменяемо */
public final class VoidEffects {

    /** Значение {@link #particleBaseY}: частиц пустоты нет */
    public static final int NO_PARTICLES = Integer.MIN_VALUE;

    private static final VoidEffects NONE = new VoidEffects(false, NO_PARTICLES, 0.0F);

    public final boolean fog;
    public final int particleBaseY;
    public final float particleIntensity;

    private VoidEffects(boolean fog, int particleBaseY, float particleIntensity) {
        this.fog = fog;
        this.particleBaseY = particleBaseY;
        this.particleIntensity = particleIntensity;
    }

    /** fog — ванильное затемнение от minY, particleBaseY — частицы ниже него + 8, particleIntensity — доля 0..1 */
    public static VoidEffects of(boolean fog, int particleBaseY, float particleIntensity) {
        if (!(particleIntensity >= 0.0F && particleIntensity <= 1.0F)) {
            throw new IllegalArgumentException("particleIntensity must be within 0..1: " + particleIntensity);
        }
        return new VoidEffects(fog, particleBaseY, particleIntensity);
    }

    /** Без тумана и частиц пустоты */
    public static VoidEffects none() {
        return NONE;
    }

    public boolean hasParticles() {
        return particleBaseY != NO_PARTICLES && particleIntensity > 0.0F;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VoidEffects)) return false;
        VoidEffects v = (VoidEffects) o;
        return fog == v.fog && particleBaseY == v.particleBaseY
            && Float.compare(particleIntensity, v.particleIntensity) == 0;
    }

    @Override
    public int hashCode() {
        return (fog ? 1 : 0) + 31 * particleBaseY + 961 * Float.floatToIntBits(particleIntensity);
    }

    @Override
    public String toString() {
        return "VoidEffects{fog=" + fog
            + ", particles="
            + (particleBaseY == NO_PARTICLES ? "none" : "below " + (particleBaseY + 8) + " x" + particleIntensity)
            + "}";
    }
}
