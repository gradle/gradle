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

package org.gradle.plugin.repository.rules;

import org.gradle.api.Incubating;

/**
 * This handler will allow a {@link org.gradle.plugin.repository.RuleBasedPluginRepository} to
 * choose which plugin should be applied. If there is no known plugin to apply, then nothing should
 * be done. If the plugin requested can be satisfied by the repository, then {@link #useModule(Object)}
 * should called with the dependency to be used for the requested plugin.
 */
@Incubating
public interface PluginDependencyHandler {

    /**
     * @return returns the PluginRequest for the requested plugin.
     */
    PluginRequest getRequestedPlugin();

    /**
     * Adds a dependency to the plugin classpath. If this method is not called, then the plugin repository does
     * not know about the plugin, and Gradle should continue looking for an implementation.
     *
     * @param dependencyNotation resolvable by {@link org.gradle.api.artifacts.dsl.DependencyHandler#create(Object)}
     *
     * @return a plugin option, to configure options about the dependency
     */
    PluginModuleOptions useModule(Object dependencyNotation);

    /**
     * Plugin was not found in this repository.
     *
     * @param reason description of what was searched.
     */
    void notFound(String reason);
}
