/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.configuration;

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Provides information about various build features supported by Gradle,
 * and their state in the current build.
 * <p>
 * An instance of this type can be injected into a task, plugin or other object by annotating
 * a public constructor or property getter method with {@code javax.inject.Inject}.
 *
 * @see BuildFeature
 * @since 8.5
 */
@ServiceScope(Scope.BuildTree.class)
public interface BuildFeatures {

    /**
     * Status of the <a href="https://docs.gradle.org/current/userguide/configuration_cache.html">Configuration Cache</a> feature configuration in the build.
     * <p>
     * Note that the status only describes the status of the feature.
     * It does not provide information on whether there was a cache hit or a miss.
     *
     * @since 8.5
     */
    BuildFeature getConfigurationCache();

    /**
     * Status of the <a href="https://docs.gradle.org/current/userguide/isolated_projects.html">Isolated Projects</a> feature in the build.
     *
     * @since 8.5
     */
    BuildFeature getIsolatedProjects();

}
