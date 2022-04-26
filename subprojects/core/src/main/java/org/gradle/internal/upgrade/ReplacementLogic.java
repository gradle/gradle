package org.gradle.internal.upgrade;

@FunctionalInterface
public interface ReplacementLogic<T> {
    T execute(Object receiver, Object[] args);
}
