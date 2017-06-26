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

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection;
import org.gradle.api.tasks.TaskDestroyables;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

public class DefaultTaskDestroyables implements TaskDestroyables, TaskDestroyablesInternal {
    private final FileResolver resolver;
    private final TaskInternal task;
    private final TaskMutator taskMutator;
    private DefaultConfigurableFileCollection destroyFiles;

    public DefaultTaskDestroyables(FileResolver resolver, TaskInternal task, TaskMutator taskMutator) {
        this.resolver = resolver;
        this.task = task;
        this.taskMutator = taskMutator;
    }

    @Override
    public void files(final Object... paths) {
        taskMutator.mutate("TaskDestroys.files(Object...)", new Runnable() {
            @Override
            public void run() {
                getFiles().from(paths);
            }
        });
    }

    @Override
    public void file(final Object path) {
        taskMutator.mutate("TaskDestroys.file(Object...)", new Runnable() {
            @Override
            public void run() {
                getFiles().from(path);
            }
        });
    }

    @Override
    public DefaultConfigurableFileCollection getFiles() {
        if (destroyFiles == null) {
            destroyFiles = new DefaultConfigurableFileCollection(task + " destroy files", resolver, null);

        }
        return destroyFiles;
    }

    @Override
    public Collection<File> getFilesReadOnly() {
        if (destroyFiles != null) {
            return destroyFiles.getFiles();
        }
        return Collections.emptySet();
    }
}
