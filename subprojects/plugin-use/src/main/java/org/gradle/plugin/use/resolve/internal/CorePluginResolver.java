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

package org.gradle.plugin.use.resolve.internal;

import org.gradle.api.Plugin;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.plugins.CorePluginRegistry;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.api.plugins.UnknownPluginException;
import org.gradle.plugin.internal.PluginId;
import org.gradle.plugin.use.internal.InvalidPluginRequestException;
import org.gradle.plugin.use.internal.PluginRequest;

public class CorePluginResolver implements PluginResolver {

    private final DocumentationRegistry documentationRegistry;
    private final PluginRegistry pluginRegistry;

    public CorePluginResolver(DocumentationRegistry documentationRegistry, PluginRegistry pluginRegistry) {
        this.documentationRegistry = documentationRegistry;
        this.pluginRegistry = pluginRegistry;
    }

    public void resolve(PluginRequest pluginRequest, PluginResolutionResult result) {
        PluginId id = pluginRequest.getId();

        if (!id.isQualified() || id.inNamespace(CorePluginRegistry.CORE_PLUGIN_NAMESPACE)) {
            try {
                Class<? extends Plugin> typeForId = pluginRegistry.getTypeForId(id.getName());
                if (pluginRequest.getVersion() != null) {
                    throw new InvalidPluginRequestException(pluginRequest,
                            "Plugin '" + id + "' is a core Gradle plugin, which cannot be specified with a version number. "
                                    + "Such plugins are versioned as part of Gradle. Please remove the version number from the declaration."
                    );
                }
                result.found(getDescription(), new SimplePluginResolution(id, typeForId));
            } catch (UnknownPluginException e) {
                result.notFound(getDescription(), String.format("not a core plugin, please see %s for available core plugins", documentationRegistry.getDocumentationFor("standard_plugins")));
            }
        } else {
            result.notFound(getDescription(), String.format("plugin is not in '%s' namespace", CorePluginRegistry.CORE_PLUGIN_NAMESPACE));
        }
    }

    public static String getDescription() {
        return "Gradle Core Plugins";
    }
}
