/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.plugins.ide.internal;

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.jspecify.annotations.NullMarked;

import static org.gradle.api.internal.ConfigurationCacheDegradation.requireDegradation;
import static org.gradle.internal.Cast.uncheckedNonnullCast;

/**
 * This class provides a place for sharing functionality with {@link IdePlugin} subclasses
 * without inadvertently making them API (as happens to public or protected members in {@link IdePlugin}).
 */
@NullMarked
public class IdePluginHelper {
    /**
     * Returns a configuration action that requires unconditional graceful degradation
     * on the consumed task.
     */
    public static Action<Task> withGracefulDegradation() {
        return task -> requireDegradation(uncheckedNonnullCast(task), "Task is not compatible with the configuration cache");
    }
}
