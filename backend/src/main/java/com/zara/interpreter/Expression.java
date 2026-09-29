package com.zara.interpreter;

public interface Expression {
    Object evaluate(Environment env);
}