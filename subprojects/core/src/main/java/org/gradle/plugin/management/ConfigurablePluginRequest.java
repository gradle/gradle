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
 * This needs a better name.
 *
 * Allows user to specify details about the plugin that needs
 * to be loaded.
 */
@Incubating
public interface ConfigurablePluginRequest extends PluginRequest {

    /**
     * Override the version to search for a plugin.
     *
     * @param version version to use
     */
    void setVersion(String version);

    /**
     * Set the artifact that should be used for this artifact.
     *
     * @param artifact to be used for a plugin request.
     */
    void setArtifact(Object artifact);
}
