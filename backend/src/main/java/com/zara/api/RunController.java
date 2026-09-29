package com.zara.api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RunController {

    private final ExecutionService executionService;

    public RunController(ExecutionService executionService) {
        this.executionService = executionService;
    }

    @PostMapping("/run")
    public RunResponse run(@RequestBody RunRequest request) {
        String code = request.getCode() == null ? "" : request.getCode();
        return executionService.run(code);
    }
}
