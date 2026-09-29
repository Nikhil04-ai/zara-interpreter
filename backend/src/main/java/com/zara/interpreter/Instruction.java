package com.zara.interpreter;

public interface Instruction {
    void execute(Environment env);
}