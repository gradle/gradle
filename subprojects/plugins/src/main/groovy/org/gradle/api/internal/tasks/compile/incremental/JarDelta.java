package org.gradle.api.internal.tasks.compile.incremental;

public interface JarDelta {
    boolean isFullRebuildNeeded();
    Iterable<String> getChangedClasses();
}
