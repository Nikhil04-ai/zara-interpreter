import java.util.Collections;
import java.util.List;

public class IfInstruction implements Instruction {

    private final Expression condition;
    private final List<Instruction> thenBody;  // run if condition is true
    private final List<Instruction> elseBody;  // run if condition is false

    // Constructor without else — elseBody is empty
    public IfInstruction(Expression condition, List<Instruction> thenBody) {
        this(condition, thenBody, Collections.emptyList());
    }

    // Constructor with else
    public IfInstruction(Expression condition,
                         List<Instruction> thenBody,
                         List<Instruction> elseBody) {
        this.condition = condition;
        this.thenBody  = thenBody;
        this.elseBody  = elseBody;
    }

    @Override
    public void execute(Environment env) {

        // Step 1: evaluate condition
        Object result = condition.evaluate(env);

        // ========================= FIX =========================
        // Ensure condition evaluates to Boolean
        // Prevents silent bugs like: when x: (x = 10)
        // =======================================================
        if (!(result instanceof Boolean)) {
            throw new RuntimeException(
                "Condition must evaluate to boolean but got: " + result
            );
        }

        // Step 2: convert to boolean
        boolean conditionIsTrue = (Boolean) result;

        // Step 3: choose branch
        List<Instruction> branch = conditionIsTrue ? thenBody : elseBody;

        // Step 4: execute branch
        branch.forEach(inst -> inst.execute(env));
    }
}
