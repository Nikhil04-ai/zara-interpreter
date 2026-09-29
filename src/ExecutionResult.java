// Result of running a ZARA program: the captured `show` output, and an error
// message if execution failed partway through (output collected before the
// failure is still returned). Mirrors the { output, error } shape the future
// web API returns.
public class ExecutionResult {

    private final String output;
    private final String error;

    public ExecutionResult(String output, String error) {
        this.output = output;
        this.error = error;
    }

    public String getOutput() { return output; }
    public String getError()  { return error;  }
}
