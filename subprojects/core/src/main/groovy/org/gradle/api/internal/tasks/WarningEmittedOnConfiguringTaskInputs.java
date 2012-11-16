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

import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.TaskInputs;

import java.util.Map;

public class WarningEmittedOnConfiguringTaskInputs implements TaskInputs {
    private final TaskInputs delegate;
    private final TaskStatusNagger taskStatusNagger;

    public WarningEmittedOnConfiguringTaskInputs(TaskInputs delegate, TaskStatusNagger taskStatusNagger) {
        this.delegate = delegate;
        this.taskStatusNagger = taskStatusNagger;
    }

    public TaskInputs files(Object... paths) {
        taskStatusNagger.nagIfTaskNotInConfigurableState("TaskInputs.files(Object...)");
        return delegate.files(paths);
    }

    public TaskInputs file(Object path) {
        taskStatusNagger.nagIfTaskNotInConfigurableState("TaskInputs.file(Object)");
        return delegate.file(path);
    }

    public TaskInputs dir(Object dirPath) {
        taskStatusNagger.nagIfTaskNotInConfigurableState("TaskInputs.dir(Object)");
        return delegate.dir(dirPath);
    }

    public TaskInputs source(Object... paths) {
        taskStatusNagger.nagIfTaskNotInConfigurableState("TaskInputs.source(Object...)");
        return delegate.source(paths);
    }

    public TaskInputs source(Object path) {
        taskStatusNagger.nagIfTaskNotInConfigurableState("TaskInputs.source(Object)");
        return delegate.source(path);
    }

    public TaskInputs sourceDir(Object path) {
        taskStatusNagger.nagIfTaskNotInConfigurableState("TaskInputs.sourceDir(Object)");
        return delegate.sourceDir(path);
    }

    public TaskInputs property(String name, Object value) {
        taskStatusNagger.nagIfTaskNotInConfigurableState("TaskInputs.property(String, Object)");
        return delegate.property(name, value);
    }

    public TaskInputs properties(Map<String, ?> properties) {
        taskStatusNagger.nagIfTaskNotInConfigurableState("TaskInputs.properties(Map)");
        return delegate.properties(properties);
    }

    public boolean getHasInputs() {
        return delegate.getHasInputs();
    }

    public FileCollection getFiles() {
        return delegate.getFiles();
    }

    public Map<String, Object> getProperties() {
        return delegate.getProperties();
    }

    public boolean getHasSourceFiles() {
        return delegate.getHasSourceFiles();
    }

    public FileCollection getSourceFiles() {
        return delegate.getSourceFiles();
    }
}
