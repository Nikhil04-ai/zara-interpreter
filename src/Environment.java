import java.util.HashMap;
import java.util.Map;
public class Environment {

    
    private final Map<String, Object> store = new HashMap<>();
    private final StringBuilder output = new StringBuilder();

    // Store or update a variable value
    public void set(String name, Object value) {
        store.put(name, value);
    }

    // Append a line of program output. Buffered per-Environment (per execution)
    // instead of going straight to System.out, so concurrent executions never
    // interleave on a shared stream.
    public void print(String line) {
        output.append(line).append('\n');
    }

    public String getOutput() {
        return output.toString();
    }

    // Retrieve a variable value
    public Object get(String name) {
        if (!store.containsKey(name)) {
            throw new RuntimeException(
                "Variable not defined: \"" + name + "\". " +
                "Did you forget to write   set " + name + " = ...   first?");
        }
        return store.get(name);
    }

    // Check if a variable has been defined
    public boolean isDefined(String name) {
        return store.containsKey(name);
    }

    // Useful for debugging — prints all current variable values
    @Override
    public String toString() {
        return "Environment" + store;
    }
}
