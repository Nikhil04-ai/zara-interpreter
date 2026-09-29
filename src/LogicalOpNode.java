public class LogicalOpNode implements Expression {

    private final Expression left;
    private final String op; // "and" or "or"
    private final Expression right;

    public LogicalOpNode(Expression left, String op, Expression right) {
        this.left = left;
        this.op = op;
        this.right = right;
    }

    @Override
    public Object evaluate(Environment env) {
        boolean leftVal = expectBoolean(left.evaluate(env));

        // Short-circuit: the right side is only evaluated when it can change the result.
        if (op.equals("and")) {
            return leftVal && expectBoolean(right.evaluate(env));
        }
        return leftVal || expectBoolean(right.evaluate(env));
    }

    private boolean expectBoolean(Object value) {
        if (!(value instanceof Boolean)) {
            throw new RuntimeException("Expected a boolean for '" + op + "' but got: " + ValueFormat.format(value));
        }
        return (Boolean) value;
    }
}
