/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.file.collections;

import groovy.lang.Closure;
import org.gradle.api.Buildable;
import org.gradle.api.Task;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.tasks.TaskOutputs;
import org.gradle.internal.file.PathToFileResolver;

import java.nio.file.Path;
import java.util.concurrent.Callable;

import static org.gradle.util.GUtil.uncheckedCall;

/**
 * <p>A {@link FileCollectionResolveContext} which is used to determine the builder dependencies of a file collection hierarchy.
 */
public class BuildDependenciesOnlyFileCollectionResolveContext implements FileCollectionResolveContext {
    private final TaskDependencyResolveContext taskContext;

    public BuildDependenciesOnlyFileCollectionResolveContext(TaskDependencyResolveContext taskContext) {
        this.taskContext = taskContext;
    }

    @Override
    public FileCollectionResolveContext push(PathToFileResolver fileResolver) {
        return this;
    }

    @Override
    public ResolvableFileCollectionResolveContext newContext() {
        // Currently not required
        throw new UnsupportedOperationException();
    }

    @Override
    public FileCollectionResolveContext add(Object element) {
        // TODO - need to sync with DefaultFileCollectionResolveContext
        if (element instanceof FileCollection) {
            taskContext.add(element);
        } else if (element instanceof MinimalFileCollection && element instanceof Buildable) {
            taskContext.add(element);
        } else if (element instanceof Task) {
            taskContext.add(element);
        } else if (element instanceof TaskOutputs) {
            TaskOutputs outputs = (TaskOutputs) element;
            taskContext.add(outputs.getFiles());
        } else if (element instanceof RegularFileProperty || element instanceof DirectoryProperty) {
            taskContext.add(element);
        } else if (element instanceof Closure) {
            Closure closure = (Closure) element;
            Object closureResult = closure.call();
            if (closureResult != null) {
                add(closureResult);
            }
        } else if (element instanceof Callable) {
            Callable callable = (Callable) element;
            Object callableResult = uncheckedCall(callable);
            if (callableResult != null) {
                add(callableResult);
            }
        } else if (element instanceof Iterable && !(element instanceof Path)) {
            // Ignore Path
            Iterable<?> iterable = (Iterable) element;
            for (Object value : iterable) {
                add(value);
            }
        } else if (element instanceof Object[]) {
            Object[] array = (Object[]) element;
            for (Object value : array) {
                add(value);
            }
        }
        // Everything else assume has no dependencies
        return this;
    }
}
