package org.gradle.api.internal.tasks.compile.remote;

import org.gradle.api.Nullable;
import org.gradle.api.tasks.WorkResult;

import java.io.Serializable;

public class CompilationResult implements WorkResult, Serializable {
    private final boolean didWork;
    private final Throwable exception;

    public CompilationResult(boolean didWork, Throwable exception) {
        this.didWork = didWork;
        this.exception = exception;
    }

    public boolean getDidWork() {
        return didWork;
    }

    @Nullable
    public Throwable getException() {
        return exception;
    }

    public boolean isSuccess() {
        return exception == null;
    }
}
