/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.plugin.resolve.internal;

import org.gradle.api.Plugin;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.api.plugins.UnknownPluginException;

public class PluginRegistryPluginResolver implements PluginResolver {

    private final DocumentationRegistry documentationRegistry;
    private final PluginRegistry pluginRegistry;

    public PluginRegistryPluginResolver(DocumentationRegistry documentationRegistry, PluginRegistry pluginRegistry) {
        this.documentationRegistry = documentationRegistry;
        this.pluginRegistry = pluginRegistry;
    }

    public PluginResolution resolve(PluginRequest pluginRequest) {
        try {
            Class<? extends Plugin> typeForId = pluginRegistry.getTypeForId(pluginRequest.getId());
            if (pluginRequest.getVersion() != null) {
                throw new InvalidPluginRequestException(
                        "Plugin '" + pluginRequest.getId() + "' is a core Gradle plugin, which cannot be specified with a version number. "
                        + "Such plugins are versioned as part of Gradle. Please remove the version number from the declaration.");
            }
            return new SimplePluginResolution(typeForId);
        } catch (UnknownPluginException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "PluginRegistryPluginResolver[" + pluginRegistry + "]";
    }

    public String getDescriptionForNotFoundMessage() {
        return String.format("Gradle Distribution Plugins (listing: %s)", documentationRegistry.getDocumentationFor("standard_plugins"));
    }
}
