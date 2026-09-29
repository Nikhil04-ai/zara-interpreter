// Shared display formatting for runtime values, used both in `show` output and
// in error messages, so a whole number reads as "5" everywhere, not "5.0" in
// one place and "5" in another.
public final class ValueFormat {

    private ValueFormat() {}

    public static String format(Object value) {
        if (value instanceof Double) {
            double d = (Double) value;
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                return String.valueOf((long) d);
            }
        }
        return String.valueOf(value);
    }
}
