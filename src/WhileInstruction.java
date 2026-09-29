import java.util.List;

public class WhileInstruction implements Instruction {

    private final Expression condition;
    private final List<Instruction> body;

    public WhileInstruction(Expression condition, List<Instruction> body) {
        this.condition = condition;
        this.body = body;
    }

    @Override
    public void execute(Environment env) {
        // No iteration cap here (unlike RepeatInstruction) — a condition-driven loop
        // legitimately needs an unbounded number of iterations. Callers that expose
        // this to untrusted input (e.g. a web API) must enforce a wall-clock timeout
        // themselves; see CLAUDE.md.
        while (true) {
            Object result = condition.evaluate(env);

            if (!(result instanceof Boolean)) {
                throw new RuntimeException("Condition must evaluate to boolean but got: " + ValueFormat.format(result));
            }

            if (!(Boolean) result) break;

            for (Instruction inst : body) {
                inst.execute(env);
            }
        }
    }
}
