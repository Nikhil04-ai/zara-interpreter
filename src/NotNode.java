public class NotNode implements Expression {

    private final Expression operand;

    public NotNode(Expression operand) {
        this.operand = operand;
    }

    @Override
    public Object evaluate(Environment env) {
        Object value = operand.evaluate(env);

        if (!(value instanceof Boolean)) {
            throw new RuntimeException("Expected a boolean after 'not' but got: " + ValueFormat.format(value));
        }

        return !((Boolean) value);
    }
}
