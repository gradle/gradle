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

import org.gradle.api.artifacts.ExternalDependency;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderConvertible;
import org.jspecify.annotations.Nullable;

/**
 * Allows plugin resolution rules to inspect a requested plugin and modify which
 * target plugin will be used.
 *
 * @since 3.5
 */
public interface PluginResolveDetails {

    /**
     * Get the plugin that was requested.
     */
    PluginRequest getRequested();

    /**
     * Sets the implementation module to use for this plugin.
     *
     * <p>
     * It accepts the following notations:
     * <ul>
     *   <li>String in a format of: 'group:name:version', for example: 'org.gradle:gradle-core:1.0'</li>
     *   <li>Map with keys 'group', 'name' and 'version'; for example: [group: 'org.gradle', name: 'gradle-core', version: '1.0']</li>
     *   <li>{@link Provider} and {@link ProviderConvertible} of {@link MinimalExternalModuleDependency}</li>
     *   <li>instance of {@link ExternalDependency}</li>
     *   <li>instance of {@link ModuleComponentSelector}</li>
     *   <li>instance of {@link ModuleVersionSelector} (deprecated)</li>
     * </ul>
     *
     * @param notation the module to use
     */
    void useModule(Object notation);

    /**
     * Sets the version of the plugin to use.
     *
     * @param version version to use
     */
    void useVersion(@Nullable String version);

    /**
     * The target plugin request to use.
     */
    PluginRequest getTarget();

}
