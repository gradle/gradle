/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.specs;

import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;

/**
 * Exposes a {@code boolean} {@link Provider} as a {@link Spec} while
 * making its task dependencies available via {@link TaskDependencyContainer} so
 * instances can be directly added as
 * {@link org.gradle.api.internal.tasks.DefaultTaskDependency task dependencies}.
 */
public class ProviderBackedSpec<T> implements Spec<T>, TaskDependencyContainer {

    private final Provider<Boolean> provider;

    public ProviderBackedSpec(Provider<Boolean> provider) {
        this.provider = provider;
    }

    @Override
    public boolean isSatisfiedBy(T element) {
        return provider.get();
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        if (provider instanceof ProviderInternal<?>) {
            ((ProviderInternal<?>) provider).visitDependencies(context);
        }
    }
}
