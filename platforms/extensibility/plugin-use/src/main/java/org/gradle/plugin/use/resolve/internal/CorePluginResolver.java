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

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyFactory;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.plugins.ClassloaderBackedPluginDescriptorLocator;
import org.gradle.api.internal.plugins.PluginDescriptor;
import org.gradle.api.internal.plugins.PluginImplementation;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.plugin.management.internal.InvalidPluginRequestException;
import org.gradle.plugin.management.internal.PluginRequestInternal;
import org.gradle.plugin.use.PluginId;
import org.gradle.util.GradleVersion;

import java.util.List;

import static java.lang.String.format;
import static org.gradle.api.internal.plugins.DefaultPluginManager.CORE_PLUGIN_NAMESPACE;

public class CorePluginResolver implements PluginResolver {

    private final DocumentationRegistry documentationRegistry;
    private final PluginRegistry pluginRegistry;
    private final DependencyFactory dependencyFactory;

    public CorePluginResolver(DocumentationRegistry documentationRegistry, PluginRegistry pluginRegistry, DependencyFactory dependencyFactory) {
        this.documentationRegistry = documentationRegistry;
        this.pluginRegistry = pluginRegistry;
        this.dependencyFactory = dependencyFactory;
    }

    @Override
    public PluginResolutionResult resolve(PluginRequestInternal pluginRequest) {
        PluginId id = pluginRequest.getId();
        if (!isCorePluginRequest(id)) {
            return PluginResolutionResult.notFound(getDescription(), format("plugin is not in '%s' namespace", CORE_PLUGIN_NAMESPACE));
        }

        PluginImplementation<?> plugin = pluginRegistry.lookup(id);
        if (plugin == null) {
            return PluginResolutionResult.notFound(getDescription(), "not a core plugin. " + documentationRegistry.getDocumentationRecommendationFor("available plugins", "plugin_reference"));
        }

        validate(pluginRequest);
        return PluginResolutionResult.found(new SimplePluginResolution(plugin, companionDependenciesOf(id, plugin, dependencyFactory)));
    }

    /**
     * The distribution-companion dependencies the plugin's descriptor declares
     * ({@link PluginDescriptor#getDistributionCompanionModules()}): module coordinates a
     * distribution plugin requires on the consuming build's script classpath, resolved at the
     * running distribution's version (a distribution plugin is versioned as part of Gradle, and its
     * companions are served by the distribution's embedded repository at that same version). The
     * resolution visits them as ordinary plugin dependencies, so the request applicator adds them
     * to the still-unresolved script classpath exactly like a repository-resolved plugin's jars.
     */
    private static List<Dependency> companionDependenciesOf(PluginId id, PluginImplementation<?> plugin, DependencyFactory dependencyFactory) {
        Class<?> pluginClass = plugin.asClass();
        ClassLoader pluginClassLoader = pluginClass == null ? null : pluginClass.getClassLoader();
        if (pluginClassLoader == null) {
            // A bootstrap-loaded (or classless) plugin implementation has no descriptor to consult.
            return ImmutableList.of();
        }
        PluginDescriptor descriptor = new ClassloaderBackedPluginDescriptorLocator(pluginClassLoader).findPluginDescriptor(id.getName());
        if (descriptor == null) {
            return ImmutableList.of();
        }
        ImmutableList.Builder<Dependency> dependencies = ImmutableList.builder();
        for (String module : descriptor.getDistributionCompanionModules()) {
            int separator = module.indexOf(':');
            if (separator <= 0 || separator == module.length() - 1) {
                throw new IllegalStateException(
                    format("Plugin '%s' declares an invalid distribution companion module '%s' in %s; expected 'group:name'.", id, module, descriptor)
                );
            }
            dependencies.add(dependencyFactory.create(
                module.substring(0, separator), module.substring(separator + 1), GradleVersion.current().getVersion()
            ));
        }
        return dependencies.build();
    }

    private static void validate(PluginRequestInternal pluginRequest) {
        if (pluginRequest.getVersion() != null) {
            throw new InvalidPluginRequestException(pluginRequest,
                getCorePluginClarification(pluginRequest) + "which cannot be specified with a version number. "
                    + "Such plugins are versioned as part of Gradle. Please remove the version number from the declaration."
            );
        }
        if (pluginRequest.getSelector() != null) {
            throw new InvalidPluginRequestException(pluginRequest,
                getCorePluginClarification(pluginRequest) + "which cannot be specified with a custom implementation artifact. "
                    + "Such plugins are versioned as part of Gradle. Please remove the custom artifact from the request."
            );
        }
        if (!pluginRequest.isApply()) {
            throw new InvalidPluginRequestException(pluginRequest,
                getCorePluginClarification(pluginRequest) + "which is already on the classpath. "
                    + "Requesting it with the 'apply false' option is a no-op."
            );
        }
    }

    private static String getCorePluginClarification(PluginRequestInternal pluginRequest) {
        return "Plugin '" + pluginRequest.getId() + "' is a core Gradle plugin, ";
    }

    private static  boolean isCorePluginRequest(PluginId id) {
        String namespace = id.getNamespace();
        return namespace == null || namespace.equals(CORE_PLUGIN_NAMESPACE);
    }

    public static String getDescription() {
        return "Gradle Core Plugins";
    }
}
