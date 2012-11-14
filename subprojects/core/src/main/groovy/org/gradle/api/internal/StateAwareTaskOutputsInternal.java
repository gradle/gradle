/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal;

import groovy.lang.Closure;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskOutputs;
import org.gradle.util.DeprecationLogger;

public class StateAwareTaskOutputsInternal implements TaskOutputsInternal {

    private final TaskOutputsInternal delegate;
    private final TaskStateInternal state;
    private final Task task;

    public StateAwareTaskOutputsInternal(TaskOutputsInternal delegate, TaskStateInternal state, Task task) {
        this.delegate = delegate;
        this.state = state;
        this.task = task;
    }

    public Spec<? super TaskInternal> getUpToDateSpec() {
        return delegate.getUpToDateSpec();
    }

    public FileCollection getPreviousFiles() {
        return delegate.getPreviousFiles();
    }

    public void setHistory(TaskExecutionHistory history) {
        delegate.setHistory(history);
    }

    public void upToDateWhen(Closure upToDateClosure) {
        nagUserIfTaskExecutionStarted("TaskOutputs.upToDateWhen(Closure)");
        delegate.upToDateWhen(upToDateClosure);
    }

    public void upToDateWhen(Spec<? super Task> upToDateSpec) {
        nagUserIfTaskExecutionStarted("TaskOutputs.upToDateWhen(Spec)");
        delegate.upToDateWhen(upToDateSpec);
    }

    public boolean getHasOutput() {
        return delegate.getHasOutput();
    }

    public FileCollection getFiles() {
        return delegate.getFiles();
    }

    public TaskOutputs files(Object... paths) {
        nagUserIfTaskExecutionStarted("TaskOutputs.files(Object...)");
        return delegate.files(paths);
    }

    public TaskOutputs file(Object path) {
        nagUserIfTaskExecutionStarted("TaskOutputs.file(Object)");
        return delegate.file(path);
    }

    public TaskOutputs dir(Object path) {
        nagUserIfTaskExecutionStarted("TaskOutputs.dir(Object)");
        return delegate.dir(path);
    }

    private void nagUserIfTaskExecutionStarted(String method) {
        if (state.getExecuting() || state.getExecuted()) {
            DeprecationLogger.nagUserAboutDeprecatedWhenTaskExecuted(method, task);
        }
    }
}
