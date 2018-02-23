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

import javax.annotation.Nullable;

/**
 * Represents an object that can be used to launch a Gradle build, such as a task.
 *
 * <p>To launch a build, you pass one or more {@link org.gradle.tooling.model.Launchable} instances
 * to either {@link org.gradle.tooling.BuildLauncher#forTasks(Iterable)} or {@link org.gradle.tooling.BuildLauncher#forLaunchables(Iterable)}.</p>
 *
 * @since 1.12
 */
@Incubating
public interface Launchable extends ProjectModel {
    /**
     * Returns the identifier for the Gradle project that this model originated from.
     *
     * @since 2.13
     */
    @Incubating
    ProjectIdentifier getProjectIdentifier();

    /**
     * Returns a human-consumable display name for this launchable.
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

    /**
     * Returns whether launchable is public or not. A public launchable is one that is considered a public 'entry point' to the build, that is interesting for
     * an end user of the build to run.
     *
     * @return Public property.
     * @since 2.1
     */
    boolean isPublic();
}
