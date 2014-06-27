/*
 * Copyright 2010 the original author or authors.
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

import groovy.lang.Closure;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskExecutionHistory;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.TaskOutputsInternal;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection;
import org.gradle.api.specs.AndSpec;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskOutputs;

public class DefaultTaskOutputs implements TaskOutputsInternal {
    private final DefaultConfigurableFileCollection outputFiles;
    private AndSpec<TaskInternal> upToDateSpec = new AndSpec<TaskInternal>();
    private TaskExecutionHistory history;
    private final TaskMutator taskMutator;

    public DefaultTaskOutputs(FileResolver resolver, TaskInternal task, TaskMutator taskMutator) {
        this.taskMutator = taskMutator;
        outputFiles = new DefaultConfigurableFileCollection(String.format("%s output files", task), resolver, null);
        outputFiles.builtBy(task);
    }

    public Spec<? super TaskInternal> getUpToDateSpec() {
        return upToDateSpec;
    }

    public void upToDateWhen(final Closure upToDateClosure) {
        taskMutator.mutate("TaskOutputs.upToDateWhen(Closure)", new Runnable() {
            public void run() {
                upToDateSpec = upToDateSpec.and(upToDateClosure);
            }
        });
    }

    public void upToDateWhen(final Spec<? super Task> spec) {
        taskMutator.mutate("TaskOutputs.upToDateWhen(Spec)", new Runnable() {
            public void run() {
                upToDateSpec = upToDateSpec.and(spec);
            }
        });
    }

    public boolean getHasOutput() {
        return !outputFiles.getFrom().isEmpty() || !upToDateSpec.getSpecs().isEmpty();
    }

    public FileCollection getFiles() {
        return outputFiles;
    }

    public TaskOutputs files(final Object... paths) {
        taskMutator.mutate("TaskOutputs.files(Object...)", new Runnable() {
            public void run() {
                outputFiles.from(paths);
            }
        });
        return this;
    }

    public TaskOutputs file(final Object path) {
        taskMutator.mutate("TaskOutputs.file(Object)", new Runnable() {
            public void run() {
                outputFiles.from(path);
            }
        });
        return this;
    }

    public TaskOutputs dir(final Object path) {
        taskMutator.mutate("TaskOutputs.dir(Object)", new Runnable() {
            public void run() {
                outputFiles.from(path);
            }
        });
        return this;
    }

    public FileCollection getPreviousFiles() {
        if (history == null) {
            throw new IllegalStateException("Task history is currently not available for this task.");
        }
        return history.getOutputFiles();
    }

    public void setHistory(TaskExecutionHistory history) {
        this.history = history;
    }
}
