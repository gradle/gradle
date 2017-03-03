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

import org.gradle.api.Incubating;

/**
 * Allows plugin resolution rules to modify a plugin request
 * before it is passed to the {@link org.gradle.plugin.repository.PluginRepository}s for resolution.
 *
 * @since 3.5
 */
@Incubating
public interface ConfigurablePluginRequest extends PluginRequest {

    /**
     * Sets the version of the plugin to use.
     *
     * @param version version to use
     */
    void setVersion(String version);

    /**
     * Sets the implementation artifact to use for this plugin.
     *
     * @param notation the artifact to use, supports the same notations as {@link org.gradle.api.artifacts.dsl.DependencyHandler}
     */
    void setArtifact(Object notation);
}
