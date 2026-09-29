package com.zara.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Uses MockMvc (dispatches through the real Spring MVC stack without binding a
// real network socket) rather than a running server + HTTP client, since this
// exercises the controller/service/JSON wiring the same way a real request
// would, without depending on the environment's ability to bind a local port.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "zara.execution.timeout-ms=300")
@AutoConfigureMockMvc
class RunControllerTest {

    @Autowired
    private MockMvc mockMvc;

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
}
