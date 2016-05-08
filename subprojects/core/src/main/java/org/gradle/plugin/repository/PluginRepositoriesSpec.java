/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugin.repository;

import org.gradle.api.Action;
import org.gradle.api.Incubating;

/**
 * Used to declare {@link org.gradle.plugin.repository.PluginRepository}s
 * <p>
 * Maven and Ivy repository added via this interface will also be prepended
 * to {@code buildscript.repository}. Any artifacts resolved from these repository
 * during the resolution of the {@code plugins {}} block will also be added to
 * the {@code buildscript.dependencies.classpath}.
 */
@Incubating
public interface PluginRepositoriesSpec {
    /**
     * Adds and configures a {@link MavenPluginRepository}.
     *
     * @param action The action to use to configure the repository.
     * @return The added repository.
     */
    MavenPluginRepository maven(Action<? super MavenPluginRepository> action);

    /**
     * Adds and configures a {@link IvyPluginRepository}.
     *
     * @param action The action to use to configure the repository.
     * @return the added repository.
     */
    IvyPluginRepository ivy(Action<? super IvyPluginRepository> action);

    /**
     * Adds the Gradle Plugin Portal (plugins.gradle.org) as a plugin repository.
     * @return The added repository.
     * @throws IllegalArgumentException if called more than once.
     */
    GradlePluginPortal gradlePluginPortal();
}
