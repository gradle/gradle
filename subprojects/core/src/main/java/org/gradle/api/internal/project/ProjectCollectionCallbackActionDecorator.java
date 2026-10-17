/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.project;

import org.gradle.api.Action;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.specs.Spec;

/**
 * The {@link CollectionCallbackActionDecorator} of the collections created for a project.
 * <p>
 * The collections are not thread-safe, and their lazily registered elements may be realized on behalf of another project,
 * for example while the configuration cache stores the state of several projects in parallel.
 * This decorator realizes the elements while holding the lock of the project in that case,
 * see {@link ProjectState#runWithModelLockIfAccessedFromOtherProject(java.util.function.Supplier)}.
 */
public class ProjectCollectionCallbackActionDecorator implements CollectionCallbackActionDecorator {
    private final CollectionCallbackActionDecorator delegate;
    private final ProjectState owner;

    public ProjectCollectionCallbackActionDecorator(CollectionCallbackActionDecorator delegate, ProjectState owner) {
        this.delegate = delegate;
        this.owner = owner;
    }

    @Override
    public <T> Action<T> decorate(Action<T> action) {
        return delegate.decorate(action);
    }

    @Override
    public <T> Spec<T> decorateSpec(Spec<T> spec) {
        return delegate.decorateSpec(spec);
    }

    @Override
    public void runRealization(Runnable realization) {
        owner.runWithModelLockIfAccessedFromOtherProject(() -> {
            realization.run();
            return null;
        });
    }
}
