/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.initialization;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.artifacts.dsl.ComponentMetadataHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.initialization.dsl.DependenciesModelBuilder;
import org.gradle.internal.HasInternalProtocol;

/**
 * Allows configuring dependency resolution for all projects of the build.
 *
 * @since 6.8
 *
 */
@Incubating
@HasInternalProtocol
public interface DependencyResolutionManagement {
    /**
     * Configures the repositories used by all projects
     * @param repositoryConfiguration the repositories configuration
     */
    void repositories(Action<? super RepositoryHandler> repositoryConfiguration);

    /**
     * If this method is called, any repository declared on a project will cause
     * the project to use the repositories declared by the project, ignoring
     * those declared in settings. A warning will be issued in such a situation.
     *
     * This is the default behavior.
     */
    void preferProjectRepositories();

    /**
     * If this method is called, any repository declared directly in a project,
     * either directly or via a plugin, will be ignored.
     */
    void preferSettingsRepositories();

    /**
     * If this method is called, any repository declared directly in a project,
     * either directly or via a plugin, will trigger a build error.
     */
    void enforceSettingsRepositories();

    /**
     * Registers component metadata rules used by all projects
     * @param registration the registration action
     */
    void components(Action<? super ComponentMetadataHandler> registration);

    /**
     * Returns the shared component metadata handler
     */
    ComponentMetadataHandler getComponents();

    /**
     * If this method is called, any component metadata rule declared on a project
     * will cause the project to use the rules declared by the project, ignoring
     * those declared in settings. A warning will be issued in such a situation.
     *
     * This is the default behavior.
     */
    void preferProjectRules();

    /**
     * If this method is called, any component metadata rule declared directly in a
     * project, either directly or via a plugin, will be ignored.
     */
    void preferSettingsRules();

    /**
     * If this method is called, any component metadata rule declared directly in a
     * project, either directly or via a plugin, will trigger a build error.
     */
    void enforceSettingsRules();

    void dependenciesModel(Action<? super DependenciesModelBuilder> spec);
}
