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

import com.google.common.collect.Lists;
import org.gradle.api.NonNullApi;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection;
import org.gradle.util.DeprecationLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

@NonNullApi
public class DefaultTaskDestroyables implements TaskDestroyablesInternal {
    private final FileResolver resolver;
    private final TaskInternal task;
    private final TaskMutator taskMutator;
    private final TaskPropertiesWalker propertiesWalker;
    private final PropertySpecFactory specFactory;
    private final List<Object> paths = Lists.newArrayList();

    public DefaultTaskDestroyables(FileResolver resolver, TaskInternal task, TaskMutator taskMutator, TaskPropertiesWalker propertiesWalker, PropertySpecFactory specFactory) {
        this.resolver = resolver;
        this.task = task;
        this.taskMutator = taskMutator;
        this.propertiesWalker = propertiesWalker;
        this.specFactory = specFactory;
    }

    @Override
    public void files(final Object... paths) {
        DeprecationLogger.nagUserOfReplacedMethod("TaskDestroys.files", "TaskDestroys.register");
        taskMutator.mutate("TaskDestroys.files(Object...)", new Runnable() {
            @Override
            public void run() {
                Collections.addAll(DefaultTaskDestroyables.this.paths, paths);
            }
        });
    }

    @Override
    public void file(final Object path) {
        DeprecationLogger.nagUserOfReplacedMethod("TaskDestroys.file", "TaskDestroys.register");
        taskMutator.mutate("TaskDestroys.file(Object...)", new Runnable() {
            @Override
            public void run() {
                paths.add(path);
            }
        });
    }

    @Override
    public void register(final Object... paths) {
        taskMutator.mutate("TaskDestroys.register(Object...)", new Runnable() {
            @Override
            public void run() {
                Collections.addAll(DefaultTaskDestroyables.this.paths, paths);
            }
        });
    }

    public void accept(InputsOutputVisitor visitor) {
        propertiesWalker.visitInputs(specFactory, visitor, task);
        for (Object path : paths) {
            visitor.visitDestroyable(path);
        }
    }

    @Override
    public FileCollection getFiles() {
        Iterable<Object> objects = new Iterable<Object>() {
            @Override
            public Iterator<Object> iterator() {
                GetFilesVisitor visitor = new GetFilesVisitor();
                accept(visitor);
                return visitor.getDestroyables().iterator();
            }
        };
        return new DefaultConfigurableFileCollection(task + " destroy files", resolver, null, objects);
    }

    private static class GetFilesVisitor extends InputsOutputVisitor.Adapter {
        private List<Object> destroyables = new ArrayList<Object>();

        @Override
        public void visitDestroyable(Object path) {
            destroyables.add(path);
        }

        public List<Object> getDestroyables() {
            return destroyables;
        }
    }
}
