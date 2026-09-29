package com.zara.api;

import com.zara.interpreter.ExecutionResult;
import com.zara.interpreter.Interpreter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

// Runs untrusted ZARA source on a bounded executor with a wall-clock timeout.
// A ZARA `while` has no iteration cap by language design (see WhileInstruction),
// so without this, one bad request could hang a thread forever. Future.cancel(true)
// only requests interruption -- it can't force a running thread to stop -- so
// WhileInstruction/RepeatInstruction cooperate by checking Thread.isInterrupted().
@Service
public class ExecutionService {

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Value("${zara.execution.timeout-ms:5000}")
    private long timeoutMs;

    public RunResponse run(String code) {
        Future<ExecutionResult> future = executor.submit(() -> new Interpreter().run(code));

        try {
            ExecutionResult result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            return new RunResponse(result.getOutput(), result.getError());

        } catch (TimeoutException e) {
            future.cancel(true);
            return new RunResponse("", "Execution timed out after " + timeoutMs + "ms");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new RunResponse("", "Execution was interrupted");

        } catch (ExecutionException e) {
            return new RunResponse("", "Internal error: " + e.getCause());
        }
    }
}
