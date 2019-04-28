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

package org.gradle.api.internal.provider;

import org.gradle.api.Transformer;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.provider.Provider;

import javax.annotation.Nullable;

public interface ProviderInternal<T> extends Provider<T>, TaskDependencyContainer {
    /**
     * Return the upper bound on the type of all values that this provider may produce, if known.
     *
     * This could probably move to the public API.
     */
    @Nullable
    Class<T> getType();

    /**
     * Visits the build dependencies of this provider, if possible.
     * @return true if the dependencies have been added (possibly none), false if the build dependencies are unknown.
     */
    boolean maybeVisitBuildDependencies(TaskDependencyResolveContext context);

    @Override
    <S> ProviderInternal<S> map(Transformer<? extends S, ? super T> transformer);

    ProviderInternal<T> withFinalValue();
}
