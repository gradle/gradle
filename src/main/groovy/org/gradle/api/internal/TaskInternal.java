package org.gradle.api.internal;

import org.gradle.api.Task;

public interface TaskInternal extends Task {
    void execute();
}
