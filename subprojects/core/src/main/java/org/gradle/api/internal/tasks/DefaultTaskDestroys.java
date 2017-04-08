/*
 * Copyright 2017 the original author or authors.
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
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection;
import org.gradle.api.tasks.TaskDestroys;

public class DefaultTaskDestroys implements TaskDestroys {
    private final DefaultConfigurableFileCollection destroyFiles;
    private final FileResolver resolver;
    private final TaskMutator taskMutator;

    public DefaultTaskDestroys(FileResolver resolver, TaskInternal task, TaskMutator taskMutator) {
        this.resolver = resolver;
        this.taskMutator = taskMutator;
        this.destroyFiles = new DefaultConfigurableFileCollection(task + " destroy files", resolver, null);
    }

    @Override
    public TaskDestroys files(final Object... paths) {
        taskMutator.mutate("TaskDestroys.files(Object...)", new Runnable() {
            @Override
            public void run() {
                destroyFiles.from(paths);
            }
        });
        return this;
    }

    @Override
    public TaskDestroys file(final Object path) {
        taskMutator.mutate("TaskDestroys.file(Object...)", new Runnable() {
            @Override
            public void run() {
                destroyFiles.from(path);
            }
        });
        return this;
    }

    @Override
    public FileCollection getFiles() {
        return destroyFiles;
    }
}
