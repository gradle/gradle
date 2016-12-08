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
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.plugin.repository.rules.PluginDependencyHandler;

/**
 * Used to describe a rule based plugin repository.
 *
 * Using this repository, the implementer is responsible to decide if the plugin can be
 * satisfied by this repository. If in doubt, then nothing should be done.
 */
@Incubating
public interface RuleBasedPluginRepository extends PluginRepository {

    /**
     * @return Description of the rule based plugin repository.
     */
    String getDescription();

    /**
     * Set the description of the rule based plugin repository.
     * @param description the description.
     */
    void setDescription(String description);

    /**
     * Configure a {@link RepositoryHandler} to describe where the artifact should come from.
     *
     * @param action to configure a {@link RepositoryHandler}.
     */
    void artifactRepositories(Action<? super RepositoryHandler> action);

    /**
     * A callback that will be called for each plugin defined in a <code>plugins {}</code> block.
     * If the repository does not provide the plugin, it should not provide an implementation.
     *
     * @param resolution a callback to provide a plugin.
     */
    void pluginResolution(Action<? super PluginDependencyHandler> resolution);
}
