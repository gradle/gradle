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
 * Represents an object that can be used to launch the build.
 *
 * This can be an existing task or a task selector used to run a build.
 *
 * @since 1.12
 */
@Incubating
public interface Launchable {
    /**
     * Returns the display name of this launchable.
     *
     * @return Display name of this launchable.
     * @since 1.12
     */
    String getDisplayName();

    /**
     * Returns the description of this launchable, or {@code null} if it has no description.
     *
     * @return The description of this launchable, or {@code null} if it has no description.
     * @since 1.12
     */
    @Nullable
    String getDescription();
}
