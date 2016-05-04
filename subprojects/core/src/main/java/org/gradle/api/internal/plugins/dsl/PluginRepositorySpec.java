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

package org.gradle.api.internal.plugins.dsl;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.internal.plugins.repositories.GradlePluginPortal;
import org.gradle.api.internal.plugins.repositories.IvyPluginRepository;
import org.gradle.api.internal.plugins.repositories.MavenPluginRepository;

/**
 * Used to declare {@link org.gradle.api.internal.plugins.repositories.PluginRepository}s
 * <p>
 * Maven and Ivy repositories added via this interface will also be prepended
 * to @{code buildscript.repositories}. They will be searched when resolving dependencies
 * declared in the @{code buildscript.dependencies} block.
 */
@Incubating
public interface PluginRepositorySpec {
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
