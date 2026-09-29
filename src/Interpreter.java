import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Interpreter {

    // Runs a ZARA program given as a source code String.
    // Returns the captured output and, if execution failed, an error message —
    // output produced before the failure is still included.
    public ExecutionResult run(String sourceCode) {
        Environment env = new Environment();

        try {
            // Stage 1: Tokenize
            List<Token> tokens = new Tokenizer(sourceCode).tokenize();

            // Stage 2: Parse
            Parser parser = new Parser(tokens);
            parser.setSource(sourceCode);   // needed for indentation block detection
            List<Instruction> instructions = parser.parse();

            // Stage 3: Execute
            instructions.forEach(inst -> inst.execute(env));

            return new ExecutionResult(env.getOutput(), null);

        } catch (RuntimeException e) {
            return new ExecutionResult(env.getOutput(), e.getMessage());
        }
    }

    // Entry point — reads a .zara file and runs it
    public static void main(String[] args) throws IOException {

        if (args.length == 0) {
            System.out.println("No file given. Running built-in demo...\n");
            runAllDemos();
            return;
        }

        String sourceCode = new String(Files.readAllBytes(Paths.get(args[0])));

        printResult(new Interpreter().run(sourceCode));
    }

    private static void printResult(ExecutionResult result) {
        System.out.print(result.getOutput());
        if (result.getError() != null) {
            System.err.println("Error: " + result.getError());
        }
    }

    private static void runAllDemos() {
        Interpreter interp = new Interpreter();

        System.out.println("=== Program 1: Arithmetic (expected: 16) ===");
        printResult(interp.run("set x = 10\nset y = 3\nset result = x + y * 2\nshow result\n"));

        System.out.println("\n=== Program 2: Strings ===");
        printResult(interp.run("set name = \"Sitare\"\nshow name\nshow \"Hello from ZARA\"\n"));

        System.out.println("\n=== Program 3: Conditional (expected: Pass) ===");
        printResult(interp.run("set score = 85\nwhen score > 50:\n    show \"Pass\"\n"));

        System.out.println("\n=== Program 4: Loop (expected: 1 2 3 4) ===");
        printResult(interp.run("set i = 1\nloop 4:\n    show i\n    set i = i + 1\n"));
    }
}
