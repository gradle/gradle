/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.plugins.DefaultPluginRegistry;
import org.gradle.api.internal.plugins.PluginDescriptorLocator;
import org.gradle.api.internal.plugins.PluginImplementation;
import org.gradle.api.internal.plugins.PluginInspector;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.plugin.management.internal.InvalidPluginRequestException;
import org.gradle.plugin.management.internal.PluginRequestInternal;
import org.gradle.plugin.management.internal.autoapply.AutoAppliedDevelocityPlugin;
import org.gradle.plugin.use.PluginId;
import org.gradle.plugin.use.tracker.internal.PluginVersionTracker;

import javax.annotation.Nullable;

/**
 * Resolves a plugin either by determining that it is already on the classpath
 * or by delegating to another resolver.
 */
public class AlreadyOnClasspathPluginResolver implements PluginResolver {
    private final PluginResolver delegate;
    private final PluginRegistry corePluginRegistry;
    private final PluginDescriptorLocator pluginDescriptorLocator;
    private final ClassLoaderScope parentLoaderScope;
    private final PluginInspector pluginInspector;
    private final PluginVersionTracker pluginVersionTracker;

    public AlreadyOnClasspathPluginResolver(
        PluginResolver delegate,
        PluginRegistry corePluginRegistry,
        ClassLoaderScope parentLoaderScope,
        PluginDescriptorLocator pluginDescriptorLocator,
        PluginInspector pluginInspector,
        PluginVersionTracker pluginVersionTracker
    ) {
        this.delegate = delegate;
        this.corePluginRegistry = corePluginRegistry;
        this.pluginDescriptorLocator = pluginDescriptorLocator;
        this.parentLoaderScope = parentLoaderScope;
        this.pluginInspector = pluginInspector;
        this.pluginVersionTracker = pluginVersionTracker;
    }

    @Override
    public PluginResolutionResult resolve(PluginRequestInternal pluginRequest) {
        PluginId pluginId = pluginRequest.getId();
        if (isCorePlugin(pluginId) || !isPresentOnClasspath(pluginId)) {
            return delegate.resolve(pluginRequest);
        }

        String version = getVersion(pluginRequest);
        if (version == null) {
            return resolveAlreadyOnClasspath(pluginId, null);
        }

        if (pluginRequest.getId().equals(AutoAppliedDevelocityPlugin.BUILD_SCAN_PLUGIN_ID) &&
            isPresentOnClasspath(AutoAppliedDevelocityPlugin.ID)
        ) {
            // The JAR that contains the enterprise plugin also contains the build scan plugin.
            // If the user is in the process of migrating to Gradle 6 and has not yet moved away from the scan plugin,
            // they might hit this scenario when running with --scan as that will have auto applied the new plugin.
            // Instead of a generic failure, we provide more specific feedback to help people upgrade.
            // We use the same message the user would have seen if they didn't use --scan and trigger the auto apply.
            throw new InvalidPluginRequestException(pluginRequest,
                "The build scan plugin is not compatible with this version of Gradle.\n"
                    + "Please see https://gradle.com/help/gradle-6-build-scan-plugin for more information."
            );
        }

        PluginVersionTracker.VersionDef existingVersion = pluginVersionTracker.findPluginVersionAt(parentLoaderScope, pluginId.getId());
        if (existingVersion == null) {
            throw new InvalidPluginRequestException(
                pluginRequest,
                "The request for this plugin could not be satisfied because " +
                    "the plugin is already on the classpath with an unknown version, so compatibility cannot be checked."
            );
        }

        if (existingVersion.isLocal()) {
            PluginResolutionResult resolved = delegate.resolve(pluginRequest);
            if (resolved.isFound() && resolved.getFound(pluginRequest).isLocal()) {
                return resolved;
            }
        }

        if (!existingVersion.getVersion().equals(version)) {
            throw new InvalidPluginRequestException(
                pluginRequest,
                "The request for this plugin could not be satisfied because " +
                    "the plugin is already on the classpath with a different version (" + existingVersion + ")."
            );
        }

        return resolveAlreadyOnClasspath(pluginId, existingVersion.getVersion());
    }

    @Nullable
    private static String getVersion(PluginRequestInternal pluginRequest) {
        if (pluginRequest.getOriginalRequest() != null) {
            return pluginRequest.getOriginalRequest().getVersion();
        }
        return pluginRequest.getVersion();
    }

    private PluginResolutionResult resolveAlreadyOnClasspath(PluginId pluginId, @Nullable String pluginVersion) {
        DefaultPluginRegistry pluginRegistry = new DefaultPluginRegistry(pluginInspector, parentLoaderScope);
        PluginImplementation<?> plugin = pluginRegistry.lookup(pluginId);
        if (plugin != null) {
            PluginResolution pluginResolution = new ClassPathPluginResolution(pluginId, pluginVersion, plugin);
            return PluginResolutionResult.found(pluginResolution);
        } else {
            return PluginResolutionResult.notFound("Classpath", "Plugin with id '" + pluginId + "' not found.");
        }
    }

    private boolean isPresentOnClasspath(PluginId pluginId) {
        return pluginDescriptorLocator.findPluginDescriptor(pluginId.toString()) != null;
    }

    private boolean isCorePlugin(PluginId pluginId) {
        return corePluginRegistry.lookup(pluginId) != null;
    }
}
