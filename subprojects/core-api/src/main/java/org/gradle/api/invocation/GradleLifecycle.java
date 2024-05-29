/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.invocation;

import org.gradle.api.Incubating;
import org.gradle.api.IsolatedAction;
import org.gradle.api.Project;
import org.gradle.api.configuration.BuildFeatures;

/**
 * Gradle lifecycle callbacks compatible with {@link BuildFeatures#getConfigurationCache() Configuration Cache}
 * and {@link BuildFeatures#getIsolatedProjects() Isolated Projects}.
 *
 * @since 8.8
 */
@Incubating
public interface GradleLifecycle {
    /**
     * Adds an {@link IsolatedAction isolated action} to be called immediately before a project is evaluated.
     *
     * Any extensions added to the {@code Project} model will be available to build scripts.
     *
     * @param action The action to execute.
     * @see IsolatedAction for the requirements to isolated actions
     * @since 8.8
     */
    @Incubating
    void beforeProject(IsolatedAction<? super Project> action);

    /**
     * Adds an {@link IsolatedAction isolated action} to be called immediately after a project is evaluated.
     *
     * @param action The action to execute.
     * @see IsolatedAction for the requirements to isolated actions
     * @since 8.8
     */
    @Incubating
    void afterProject(IsolatedAction<? super Project> action);
}
