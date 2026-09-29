package com.zara.api;

public class RunResponse {

    private final String output;
    private final String error;

    public RunResponse(String output, String error) {
        this.output = output;
        this.error = error;
    }

    public String getOutput() { return output; }
    public String getError()  { return error;  }
}
