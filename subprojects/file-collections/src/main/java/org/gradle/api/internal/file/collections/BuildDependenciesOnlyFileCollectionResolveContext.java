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

import org.gradle.api.Buildable;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.internal.provider.ValueSupplier;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.internal.file.PathToFileResolver;

/**
 * <p>A {@link FileCollectionResolveContext} which is used to determine the builder dependencies of a file collection hierarchy.
 */
public class BuildDependenciesOnlyFileCollectionResolveContext implements FileCollectionResolveContext {
    private final TaskDependencyResolveContext taskContext;

    public BuildDependenciesOnlyFileCollectionResolveContext(TaskDependencyResolveContext taskContext) {
        this.taskContext = taskContext;
    }

    @Override
    public ResolvableFileCollectionResolveContext newContext() {
        // Currently not required
        throw new UnsupportedOperationException();
    }

    @Override
    public FileCollectionResolveContext add(Object element) {
        maybeAdd(element);
        return this;
    }

    @Override
    public boolean maybeAdd(Object element) {
        if (element instanceof ProviderInternal) {
            ProviderInternal<?> provider = (ProviderInternal<?>) element;
            ValueSupplier.ValueProducer producer = provider.getProducer();
            if (producer.isKnown()) {
                producer.visitProducerTasks(taskContext);
                return true;
            } else {
                return false;
            }
        } else if (element instanceof TaskDependencyContainer || element instanceof Buildable) {
            taskContext.add(element);
        } else if (!(element instanceof MinimalFileCollection)) {
            throw new IllegalArgumentException("Don't know how to determine the build dependencies of " + element);
        } // else ignore
        return true;
    }

    @Override
    public FileCollectionResolveContext addAll(Iterable<?> elements) {
        for (Object element : elements) {
            add(element);
        }
        return this;
    }

    @Override
    public FileCollectionResolveContext add(Object element, PathToFileResolver resolver) {
        // Assume individual files have no dependencies
        return this;
    }
}
