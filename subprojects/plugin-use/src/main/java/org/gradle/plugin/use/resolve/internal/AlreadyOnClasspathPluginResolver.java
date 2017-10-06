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
import org.gradle.api.internal.plugins.PluginDescriptorLocator;
import org.gradle.api.internal.plugins.PluginInspector;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.plugin.management.internal.InvalidPluginRequestException;
import org.gradle.plugin.management.internal.PluginRequestInternal;
import org.gradle.plugin.use.PluginId;

public class AlreadyOnClasspathPluginResolver implements PluginResolver {

    private static final Factory<ClassPath> EMPTY_CLASSPATH_FACTORY = Factories.constant(ClassPath.EMPTY);

    private final PluginResolver delegate;
    private final PluginRegistry corePluginRegistry;
    private final PluginDescriptorLocator pluginDescriptorLocator;
    private final ClassLoaderScope parentLoaderScope;
    private final PluginInspector pluginInspector;

    public AlreadyOnClasspathPluginResolver(PluginResolver delegate, PluginRegistry corePluginRegistry, ClassLoaderScope parentLoaderScope, PluginDescriptorLocator pluginDescriptorLocator, PluginInspector pluginInspector) {
        this.delegate = delegate;
        this.corePluginRegistry = corePluginRegistry;
        this.pluginDescriptorLocator = pluginDescriptorLocator;
        this.parentLoaderScope = parentLoaderScope;
        this.pluginInspector = pluginInspector;
    }

    public void resolve(PluginRequestInternal pluginRequest, PluginResolutionResult result) {
        PluginId pluginId = pluginRequest.getId();
        if (isCorePlugin(pluginId) || isAbsentFromTheClasspath(pluginId)) {
            delegate.resolve(pluginRequest, result);
        } else if (pluginRequest.getOriginalRequest().getVersion() != null) {
            throw new InvalidPluginRequestException(pluginRequest, "Plugin request for plugin already on the classpath must not include a version");
        } else {
            resolveAlreadyOnClasspath(pluginId, result);
        }
    }

    private void resolveAlreadyOnClasspath(PluginId pluginId, PluginResolutionResult result) {
        PluginResolution pluginResolution = new ClassPathPluginResolution(pluginId, parentLoaderScope, EMPTY_CLASSPATH_FACTORY, pluginInspector);
        result.found("Already on classpath", pluginResolution);
    }

    private boolean isAbsentFromTheClasspath(PluginId pluginId) {
        return pluginDescriptorLocator.findPluginDescriptor(pluginId.toString()) == null;
    }

    private boolean isCorePlugin(PluginId pluginId) {
        return corePluginRegistry.lookup(pluginId) != null;
    }
}
