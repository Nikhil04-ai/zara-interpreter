package com.zara.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Separate application context from RunControllerTest because these cases need a
// realistic timeout window (a legitimate 50,000-iteration loop needs more than the
// 300ms used there to test *actual* timeouts) -- a different @SpringBootTest
// properties set means Spring gives it its own cached context.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "zara.execution.timeout-ms=3000")
@AutoConfigureMockMvc
class RunControllerSafetyTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void largeButLegitimateLoopCompletesSuccessfullyInsteadOfBeingKilledByTheTimeout() throws Exception {
        String code = "set sum = 0\nset i = 0\nloop 50000:\n    set sum = sum + i\n    set i = i + 1\nshow sum\n";
        mockMvc.perform(post("/api/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code.replace("\n", "\\n") + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.output").value("1249975000\n"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void deeplyNestedParenthesesReturnACleanErrorInsteadOfCrashingTheServer() throws Exception {
        String nested = "(".repeat(20000) + "1" + ")".repeat(20000);
        String code = "show " + nested + "\n";

        mockMvc.perform(post("/api/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code.replace("\n", "\\n") + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("Program is nested too deeply to evaluate"));
    }

    @Test
    void concurrentRequestsDoNotCrossContaminateEachOthersOutput() throws Exception {
        int requestCount = 8;
        ExecutorService clientPool = Executors.newFixedThreadPool(requestCount);

        try {
            List<Callable<Void>> tasks = new java.util.ArrayList<>();
            for (int i = 0; i < requestCount; i++) {
                int n = i;
                tasks.add(() -> {
                    String code = "show " + n + " * " + n + "\n";
                    mockMvc.perform(post("/api/run")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"code\":\"" + code.replace("\n", "\\n") + "\"}"))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.output").value((n * n) + "\n"))
                            .andExpect(jsonPath("$.error").doesNotExist());
                    return null;
                });
            }

            List<Future<Void>> results = clientPool.invokeAll(tasks, 20, TimeUnit.SECONDS);

            for (Future<Void> result : results) {
                assertThat(result.isDone()).isTrue();
                result.get(); // rethrows if the assertion inside the task failed
            }
        } finally {
            clientPool.shutdownNow();
        }
    }
}
