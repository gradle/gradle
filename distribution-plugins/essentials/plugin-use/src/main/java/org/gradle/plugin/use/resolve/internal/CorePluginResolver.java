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

import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.plugins.PluginImplementation;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.plugin.management.internal.InvalidPluginRequestException;
import org.gradle.plugin.management.internal.PluginRequestInternal;
import org.gradle.plugin.use.PluginId;

import static java.lang.String.format;
import static org.gradle.api.internal.plugins.DefaultPluginManager.CORE_PLUGIN_NAMESPACE;

public class CorePluginResolver implements PluginResolver {

    private final DocumentationRegistry documentationRegistry;
    private final PluginRegistry pluginRegistry;

    public CorePluginResolver(DocumentationRegistry documentationRegistry, PluginRegistry pluginRegistry) {
        this.documentationRegistry = documentationRegistry;
        this.pluginRegistry = pluginRegistry;
    }

    @Override
    public void resolve(PluginRequestInternal pluginRequest, PluginResolutionResult result) {
        PluginId id = pluginRequest.getId();
        if (!isCorePluginRequest(id)) {
            result.notFound(getDescription(), format("plugin is not in '%s' namespace", CORE_PLUGIN_NAMESPACE));
            return;
        }

        PluginImplementation<?> plugin = pluginRegistry.lookup(id);
        if (plugin == null) {
            result.notFound(getDescription(), format("not a core plugin, please see %s for available core plugins", documentationRegistry.getDocumentationFor("standard_plugins")));
            return;
        }

        validate(pluginRequest);
        result.found(getDescription(), new SimplePluginResolution(plugin));
    }

    private void validate(PluginRequestInternal pluginRequest) {
        if (pluginRequest.getVersion() != null) {
            throw new InvalidPluginRequestException(pluginRequest,
                "Plugin '" + pluginRequest.getId() + "' is a core Gradle plugin, which cannot be specified with a version number. "
                    + "Such plugins are versioned as part of Gradle. Please remove the version number from the declaration."
            );
        }
        if (pluginRequest.getModule() != null) {
            throw new InvalidPluginRequestException(pluginRequest,
                "Plugin '" + pluginRequest.getId() + "' is a core Gradle plugin, which cannot be specified with a custom implementation artifact. "
                    + "Such plugins are versioned as part of Gradle. Please remove the custom artifact from the request."
            );
        }
        if (!pluginRequest.isApply()) {
            throw new InvalidPluginRequestException(pluginRequest,
                "Plugin '" + pluginRequest.getId() + "' is a core Gradle plugin, which is already on the classpath. "
                    + "Requesting it with the 'apply false' option is a no-op."
            );
        }
    }

    private boolean isCorePluginRequest(PluginId id) {
        String namespace = id.getNamespace();
        return namespace == null || namespace.equals(CORE_PLUGIN_NAMESPACE);
    }

    public static String getDescription() {
        return "Gradle Core Plugins";
    }
}
