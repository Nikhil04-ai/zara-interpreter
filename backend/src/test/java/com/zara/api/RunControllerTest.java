package com.zara.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Uses MockMvc (dispatches through the real Spring MVC stack without binding a
// real network socket) rather than a running server + HTTP client, since this
// exercises the controller/service/JSON/CORS wiring the same way a real request
// would, without depending on the environment's ability to bind a local port.
// Uses a short timeout so the deliberately-slow tests here stay fast; see
// RunControllerSafetyTest for tests that need a realistic timeout window.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "zara.execution.timeout-ms=300")
@AutoConfigureMockMvc
class RunControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // ===================== Happy path / error shape =====================

    @Test
    void runsSimpleProgramAndReturnsOutput() throws Exception {
        mockMvc.perform(post("/api/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"show 1 + 2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.output").value("3\n"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void runtimeErrorIsReturnedInErrorField() throws Exception {
        mockMvc.perform(post("/api/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"set x = 5 / 0\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value("Division by zero"));
    }

    @Test
    void partialOutputIsKeptWhenExecutionFailsPartway() throws Exception {
        mockMvc.perform(post("/api/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"show \\\"before\\\"\\nset x = 5 / 0\\n\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.output").value("before\n"))
                .andExpect(jsonPath("$.error").value("Division by zero"));
    }

    @Test
    void infiniteLoopIsStoppedByTheExecutionTimeoutInsteadOfHangingTheRequest() throws Exception {
        mockMvc.perform(post("/api/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"while 1 == 1:\\n    set x = 1\\n\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(containsString("timed out")));
    }

    @Test
    void malformedRequestBodyStillReturnsTheSameOutputErrorShape() throws Exception {
        mockMvc.perform(post("/api/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    // ===================== Request edge cases =====================

    @Test
    void emptyCodeStringProducesEmptyOutputAndNoError() throws Exception {
        mockMvc.perform(post("/api/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.output").value(""))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void missingCodeFieldIsTreatedAsAnEmptyProgramRatherThanErroring() throws Exception {
        mockMvc.perform(post("/api/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.output").value(""))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void missingRequestBodyIsRejectedCleanlyWithoutLeakingInternalDetails() throws Exception {
        mockMvc.perform(post("/api/run")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("Expected: {\"code\"")))
                .andExpect(jsonPath("$.error", org.hamcrest.Matchers.not(containsString("com.zara"))));
    }

    @Test
    void getMethodOnRunEndpointReturnsProperMethodNotAllowedStatus() throws Exception {
        mockMvc.perform(get("/api/run"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value(containsString("GET")));
    }

    // ===================== CORS =====================

    @Test
    void corsPreflightAllowsLocalhostOrigin() throws Exception {
        mockMvc.perform(options("/api/run")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }

    @Test
    void corsPreflightAllowsVercelDeploymentOrigin() throws Exception {
        mockMvc.perform(options("/api/run")
                        .header(HttpHeaders.ORIGIN, "https://zara-frontend.vercel.app")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://zara-frontend.vercel.app"));
    }

    @Test
    void corsPreflightRejectsAnUnrelatedOrigin() throws Exception {
        mockMvc.perform(options("/api/run")
                        .header(HttpHeaders.ORIGIN, "https://evil-example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    // ===================== Actuator =====================

    @Test
    void healthEndpointReportsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    // ===================== Full language coverage through the real HTTP/JSON path =====================
    // (The interpreter's own 64-case suite already covers this at the Java level;
    // this confirms none of it gets lost or mangled going through JSON + the API.)

    @Test
    void exercisesPrecedenceParensConditionalsAndLogicTogether() throws Exception {
        String code = "set a = (2 + 3) * 4\n"
                + "when a > 15 and not (a > 100):\n"
                + "    show \"in range\"\n"
                + "otherwise:\n"
                + "    show \"out of range\"\n"
                + "show a\n";
        mockMvc.perform(post("/api/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code.replace("\n", "\\n").replace("\"", "\\\"") + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.output").value("in range\n20\n"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void exercisesWhileLoopAndComparisonOperatorsTogether() throws Exception {
        String code = "set i = 0\n"
                + "while i <= 3:\n"
                + "    when i != 2:\n"
                + "        show i\n"
                + "    set i = i + 1\n";
        mockMvc.perform(post("/api/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code.replace("\n", "\\n") + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.output").value("0\n1\n3\n"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void exercisesNestedLoops() throws Exception {
        String code = "loop 2:\n    loop 2:\n        show \"x\"\n";
        mockMvc.perform(post("/api/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code.replace("\n", "\\n").replace("\"", "\\\"") + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.output").value("x\nx\nx\nx\n"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    // ===================== Resilience under repeated abuse =====================

    @Test
    void repeatedTimeoutsDoNotLeaveTheServerUnresponsiveAfterward() throws Exception {
        String infiniteLoop = "{\"code\":\"while 1 == 1:\\n    set x = 1\\n\"}";

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/run")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(infiniteLoop))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.error").value(containsString("timed out")));
        }

        // Server must still handle a normal request correctly afterward -- proves the
        // cooperative-interruption fix actually frees up worker threads for reuse
        // instead of leaking one per timed-out request.
        mockMvc.perform(post("/api/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"show 42\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.output").value("42\n"));
    }
}
