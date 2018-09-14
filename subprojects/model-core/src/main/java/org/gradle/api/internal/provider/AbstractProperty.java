/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;

public abstract class AbstractProperty<T> implements PropertyInternal<T>, TaskDependencyContainer {
    @Override
    public T getOrElse(T defaultValue) {
        T value = getOrNull();
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    @Override
    public boolean maybeVisitBuildDependencies(TaskDependencyResolveContext context) {
        return false;
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        if (!maybeVisitBuildDependencies(context)) {
            context.maybeAdd(get());
        }
    }
}
