import java.util.List;

public class RepeatInstruction implements Instruction {

    private final int count;
    private final List<Instruction> body;

    public RepeatInstruction(int count, List<Instruction> body) {
        this.count = count;
        this.body  = body;
    }

    @Override
    public void execute(Environment env) {

        // ========================= FIX 1 =========================
        // Prevent negative loop count
        // ========================================================
        if (count < 0) {
            throw new RuntimeException("Loop count cannot be negative");
        }

        // ========================= FIX 2 =========================
        // Prevent extremely large loops (performance safeguard)
        // ========================================================
        if (count > 100000) {
            throw new RuntimeException("Loop count too large");
        }

        // Execute loop body 'count' times
        for (int i = 0; i < count; i++) {
            body.forEach(inst -> inst.execute(env));
        }
    }
}
