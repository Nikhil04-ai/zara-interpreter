public class AssignInstruction implements Instruction {

    private final String variableName;
    private final Expression expression;

    public AssignInstruction(String variableName, Expression expression) {
        this.variableName = variableName;
        this.expression = expression;
    }

    @Override
    public void execute(Environment env) {

        // ========================= FIX 1 =========================
        // Prevent using "null" as a variable name
        // =========================================================
        if (variableName.equals("null")) {
            throw new RuntimeException("Invalid variable name: null"); // FIX
        }

        // Step 1: evaluate the expression (right-hand side)
        Object value = expression.evaluate(env);

        // ========================= FIX 2 (OPTIONAL) =========================
        // Prevent assigning null values (safety improvement)
        // =========================================================
        if (value == null) {
            throw new RuntimeException("Cannot assign null to variable: " + variableName); // FIX
        }

        // Step 2: store value in environment
        env.set(variableName, value);
    }
}
