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
import org.gradle.internal.HasInternalProtocol;

/**
 * Container for the plugin, with options on how to load it.
 */
@Incubating
@HasInternalProtocol
public interface PluginModuleOptions {

    /**
     * Will isolate the plugin's classpath from other plugins that are loaded.
     *
     * @return this
     */
    @Incubating
    PluginModuleOptions withIsolatedClasspath();

    /**
     * @return the dependency notation given to {@link PluginDependencyHandler#useModule(java.lang.Object)}
     */
    Object getDependencyNotation();

    /**
     * Changes the name of the plugin that should be applied. This could be used to remove the namespace from
     * the plugin that was requested.
     *
     * @param pluginName Plugin that should be applied from the dependency.
     * @return this.
     */
    PluginModuleOptions withPluginName(String pluginName);

    /**
     * @return the name of the plugin to apply.
     */
    String getPluginName();
}
