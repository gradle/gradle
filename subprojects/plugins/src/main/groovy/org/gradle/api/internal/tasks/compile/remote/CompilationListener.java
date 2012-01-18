package org.gradle.api.internal.tasks.compile.remote;

public interface CompilationListener {
    void stdOut(CharSequence message);
    void stdErr(CharSequence message);
    void completed(CompilationResult result);
}
