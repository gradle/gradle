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
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.internal.properties.PropertyVisitor;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@NullMarked
public class DefaultTaskDestroyables implements TaskDestroyablesInternal {
    private final TaskMutator taskMutator;
    private final FileCollectionFactory fileCollectionFactory;
    private final List<Object> registeredPaths = new ArrayList<>();

    public DefaultTaskDestroyables(TaskMutator taskMutator, FileCollectionFactory fileCollectionFactory) {
        this.taskMutator = taskMutator;
        this.fileCollectionFactory = fileCollectionFactory;
    }

    @Override
    public void register(final Object... paths) {
        taskMutator.mutate("TaskDestroys.register(Object...)", () -> {
            Collections.addAll(DefaultTaskDestroyables.this.registeredPaths, paths);
        });
    }

    @Override
    public void visitRegisteredProperties(PropertyVisitor visitor) {
        for (Object registeredPath : registeredPaths) {
            visitor.visitDestroyableProperty(registeredPath);
        }
    }

    @Override
    public FileCollection getRegisteredFiles() {
        return fileCollectionFactory.resolving("destroyables", registeredPaths);
    }
}
