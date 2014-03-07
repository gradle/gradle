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
package org.gradle.tooling.model;

import org.gradle.api.Incubating;
import org.gradle.api.Nullable;

/**
 * Represents an arbitrary entry point to the build.
 *
 * This can be an existing task or a task selector used to run a build.
 *
 * @since 1.12
 */
@Incubating
public interface EntryPoint {
    /**
     * Returns the name of this entry point. Note that the name is not necessarily a unique identifier for the entry point.
     *
     * @return The name of this task.
     * @since 1.12
     */
    String getName();

    /**
     * Returns the description of this entry point, or {@code null} if it has no description.
     *
     * @return The description of this entry point, or {@code null} if it has no description.
     * @since 1.12
     */
    @Nullable
    String getDescription();
}
