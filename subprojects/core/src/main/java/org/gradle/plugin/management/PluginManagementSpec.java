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

package org.gradle.plugin.management;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.plugin.repository.PluginRepositoriesSpec;

/**
 * Configure how a plugin is resolved. By using this spec you can change how a plugin gets resolved.
 *
 * Some examples of things that are mutable are:
 * <ul>
 *     <li>Version of a plugin</li>
 *     <li>Where artifact should be used for the artifact</li>
 * </ul>
 *
 * @since 3.5
 */
@Incubating
public interface PluginManagementSpec {

    /**
     * Defines repositories to download artifacts from.
     *
     * @param repositoriesAction spec to configure {@link PluginRepositoriesSpec}
     * @since 3.5
     */
    void repositories(Action<? super PluginRepositoriesSpec> repositoriesAction);

    /**
     * @since 3.5
     * @return {@link PluginRepositoriesSpec} for repository definition.
     */
    PluginRepositoriesSpec getRepositories();

    /**
     * Configure the plugin resolution strategy.
     *
     * @since 3.5
     * @param action to configure the {@link PluginResolutionStrategy}
     */
    void resolutionStrategy(Action<? super PluginResolutionStrategy> action);

    /**
     * @since 3.5
     * @return {@link PluginResolutionStrategy} that configures plugins.
     */
    PluginResolutionStrategy getResolutionStrategy();

}
