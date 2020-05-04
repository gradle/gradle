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

package org.gradle.api.internal.tasks.properties;

import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.provider.Provider;
import org.gradle.internal.scan.UsedByScanPlugin;

import javax.annotation.Nullable;
import java.util.concurrent.Callable;

/**
 * A supplier of a property value. The property value may not necessarily be final and may change over time.
 */
@UsedByScanPlugin("test-distribution")
public interface PropertyValue extends Callable<Object> {
    /**
     * The value of the underlying property, replacing an empty provider by {@literal null}.
     *
     * This is required for allowing optional provider properties - all code which unpacks providers calls {@link Provider#get()} and would fail if an optional provider is passed.
     * Returning {@literal null} from a {@link Callable} is ignored, and {@link PropertyValue} is a {@link Callable}.
     */
    @Nullable
    @Override
    @UsedByScanPlugin("test-distribution")
    Object call();

    /**
     * The unprocessed value of the underlying property.
     */
    @Nullable
    Object getUnprocessedValue();

    /**
     * Returns the dependencies of the property value, if supported by the value implementation. Returns an empty collection if not supported or the value has no producer tasks.
     */
    TaskDependencyContainer getTaskDependencies();

    /**
     * Finalizes the property value, if possible. This makes the value final, so that it no longer changes, but not necessarily immutable.
     */
    void maybeFinalizeValue();
}
