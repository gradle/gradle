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

package org.gradle.api.internal.tasks;

import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.TaskInputs;
import org.gradle.util.DeprecationLogger;

import java.util.Map;

public class WarningEmittedOnConfiguringTaskInputs implements TaskInputs {
    private final TaskInputs delegate;
    private final Task task;

    public WarningEmittedOnConfiguringTaskInputs(TaskInputs delegate, Task task) {
        this.delegate = delegate;
        this.task = task;
    }

    private void nagUserIfTaskExecutionStarted(String method) {
        DeprecationLogger.nagUserAboutDeprecatedWhenTaskExecuted(method, task.toString());
    }

    public boolean getHasInputs() {
        return delegate.getHasInputs();
    }

    public FileCollection getFiles() {
        return delegate.getFiles();
    }

    public TaskInputs files(Object... paths) {
        nagUserIfTaskExecutionStarted("TaskInputs.files(Object...)");
        return delegate.files(paths);
    }

    public TaskInputs file(Object path) {
        nagUserIfTaskExecutionStarted("TaskInputs.file(Object)");
        return delegate.file(path);
    }

    public TaskInputs dir(Object dirPath) {
        nagUserIfTaskExecutionStarted("TaskInputs.dir(Object)");
        return delegate.dir(dirPath);
    }

    public Map<String, Object> getProperties() {
        return delegate.getProperties();
    }

    public TaskInputs property(String name, Object value) {
        nagUserIfTaskExecutionStarted("TaskInputs.property(String, Object)");
        return delegate.property(name, value);
    }

    public TaskInputs properties(Map<String, ?> properties) {
        nagUserIfTaskExecutionStarted("TaskInputs.properties(Map)");
        return delegate.properties(properties);
    }

    public boolean getHasSourceFiles() {
        return delegate.getHasSourceFiles();
    }

    public FileCollection getSourceFiles() {
        return delegate.getSourceFiles();
    }

    public TaskInputs source(Object... paths) {
        nagUserIfTaskExecutionStarted("TaskInputs.source(Object...)");
        return delegate.source(paths);
    }

    public TaskInputs source(Object path) {
        nagUserIfTaskExecutionStarted("TaskInputs.source(Object)");
        return delegate.source(path);
    }

    public TaskInputs sourceDir(Object path) {
        nagUserIfTaskExecutionStarted("TaskInputs.sourceDir(Object)");
        return delegate.sourceDir(path);
    }
}
