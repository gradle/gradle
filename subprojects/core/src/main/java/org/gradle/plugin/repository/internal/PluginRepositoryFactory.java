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

package org.gradle.plugin.repository.internal;

import org.gradle.api.Action;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.plugin.repository.GradlePluginPortal;
import org.gradle.plugin.repository.IvyPluginRepository;
import org.gradle.plugin.repository.MavenPluginRepository;

public interface PluginRepositoryFactory {
    /**
     * Adds and configures a {@link MavenPluginRepository}.
     *
     * @param action The action to use to configure the repository.
     * @param scriptFileResolver The {@link FileResolver} in the context of the Script.
     * @return The added repository.
     */
    MavenPluginRepository maven(Action<? super MavenPluginRepository> action, FileResolver scriptFileResolver);

    /**
     * Adds and configures a {@link IvyPluginRepository}.
     *
     * @param action The action to use to configure the repository.
     * @param scriptFileResolver The {@link FileResolver} in the context of the Script.
     * @return the added repository.
     */
    IvyPluginRepository ivy(Action<? super IvyPluginRepository> action, FileResolver scriptFileResolver);

    /**
     * Adds the Gradle Plugin Portal (plugins.gradle.org) as a plugin repository.
     * @return The added repository.
     * @throws IllegalArgumentException if called more than once.
     */
    GradlePluginPortal gradlePluginPortal();
}
