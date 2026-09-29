import java.util.Map;
import java.util.function.BiFunction;

public class BinaryOpNode implements Expression {

    // Operator map using lambda expressions (OCP ✔)
    private static final Map<String, BiFunction<Object, Object, Object>> OPS = Map.of(

        // ========================= FIX 3 =========================
        // Use formatValue() instead of toString() to avoid "105.0"
        // =========================================================
        "+", (l, r) -> isStringConcatenation(l, r)
                ? formatValue(l) + formatValue(r)   // FIX
                : toDouble(l) + toDouble(r),

        "-", (l, r) -> toDouble(l) - toDouble(r),

        "*", (l, r) -> toDouble(l) * toDouble(r),

        // ========================= FIX 1 =========================
        // Division by zero handling
        // =========================================================
        "/", (l, r) -> {
            double divisor = toDouble(r);
            if (divisor == 0) {
                throw new RuntimeException("Division by zero"); // FIX
            }
            return toDouble(l) / divisor;
        },

        ">", (l, r) -> toDouble(l) > toDouble(r),

        "<", (l, r) -> toDouble(l) < toDouble(r),

        ">=", (l, r) -> toDouble(l) >= toDouble(r),

        "<=", (l, r) -> toDouble(l) <= toDouble(r),

        // ========================= FIX 2 =========================
        // Null-safe + better equality handling
        // =========================================================
        "==", (l, r) -> valuesEqual(l, r),

        "!=", (l, r) -> !valuesEqual(l, r)
    );

    private final Expression left;
    private final String op;
    private final Expression right;

    public BinaryOpNode(Expression left, String op, Expression right) {
        this.left = left;
        this.op = op;
        this.right = right;
    }

    @Override
    public Object evaluate(Environment env) {

        // Step 1: evaluate both sides
        Object leftVal = left.evaluate(env);
        Object rightVal = right.evaluate(env);

        // Step 2: find operation
        BiFunction<Object, Object, Object> operation = OPS.get(op);

        if (operation == null) {
            throw new RuntimeException("Unknown operator: " + op);
        }

        // Step 3: apply operation
        return operation.apply(leftVal, rightVal);
    }

    private static boolean valuesEqual(Object l, Object r) {
        if (l == null || r == null) {
            return l == r;
        }
        if (l instanceof Double && r instanceof Double) {
            return toDouble(l) == toDouble(r);
        }
        return l.toString().equals(r.toString());
    }

    // Check if string concatenation needed
    private static boolean isStringConcatenation(Object l, Object r) {
        return l instanceof String || r instanceof String;
    }

    // ========================= FIX 3 =========================
    // Remove .0 from whole numbers when converting to String
    // =========================================================
    private static String formatValue(Object val) {
        if (val instanceof Double) {
            double d = (Double) val;
            if (d == (int) d) {
                return String.valueOf((int) d); // remove .0
            }
        }
        return val.toString();
    }

    // Safe conversion to double
    private static double toDouble(Object val) {
        if (val instanceof Double) return (Double) val;

        throw new RuntimeException(
            "Expected a number but got: \"" + val + "\" (" + val.getClass().getSimpleName() + ")"
        );
    }
}
