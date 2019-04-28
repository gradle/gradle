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

import org.gradle.api.Buildable;
import org.gradle.api.Task;
import org.gradle.api.internal.provider.PropertyInternal;
import org.gradle.api.internal.tasks.properties.PropertyValue;
import org.gradle.api.provider.Provider;

import javax.annotation.Nullable;

/**
 * A {@link PropertyValue} backed by a fixed value.
 */
public class StaticValue implements PropertyValue {
    private final Object value;

    public StaticValue(@Nullable Object value) {
        this.value = value;
    }

    public static PropertyValue of(@Nullable Object value) {
        return new StaticValue(value);
    }

    @Override
    public TaskDependencyContainer getTaskDependencies() {
        if (value instanceof TaskDependencyContainer) {
            return (TaskDependencyContainer) value;
        } else if (value instanceof Buildable) {
            return new TaskDependencyContainer() {
                @Override
                public void visitDependencies(TaskDependencyResolveContext context) {
                    context.add(value);
                }
            };
        }

        return TaskDependencyContainer.EMPTY;
    }

    @Override
    public void attachProducer(Task producer) {
        if (value instanceof PropertyInternal) {
            ((PropertyInternal)value).attachProducer(producer);
        }
    }

    @Override
    public void maybeFinalizeValue() {
        if (value instanceof PropertyInternal) {
            ((PropertyInternal)value).finalizeValueOnReadAndWarnAboutChanges();
        }
    }

    @Nullable
    @Override
    public Object call() {
        // Replace absent Provider with null.
        // This is required for allowing optional provider properties - all code which unpacks providers calls Provider.get() and would fail if an optional provider is passed.
        // Returning null from a Callable is ignored, and PropertyValue is a callable.
        if (value instanceof Provider && !((Provider<?>) value).isPresent()) {
            return null;
        }
        return value;
    }

    @Nullable
    @Override
    public Object getUnprocessedValue() {
        return value;
    }
}
