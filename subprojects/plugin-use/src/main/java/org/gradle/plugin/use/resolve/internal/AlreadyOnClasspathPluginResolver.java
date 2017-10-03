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
import org.gradle.plugin.management.PluginRequest;
import org.gradle.plugin.management.internal.InvalidPluginRequestException;
import org.gradle.plugin.use.PluginId;
import org.gradle.plugin.management.internal.PluginRequestInternal;

import javax.annotation.Nullable;
import java.util.List;

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
        } else {
            validatesRequestedVersion(pluginRequest);
            resolveAlreadyOnClasspath(pluginId, result);
        }
    }

    private void resolveAlreadyOnClasspath(PluginId pluginId, PluginResolutionResult result) {
        PluginResolution pluginResolution = new ClassPathPluginResolution(pluginId, parentLoaderScope, EMPTY_CLASSPATH_FACTORY, pluginInspector);
        result.found("Already on classpath", pluginResolution);
    }

    private void validatesRequestedVersion(PluginRequestInternal pluginRequest) {
        PluginId pluginId = pluginRequest.getId();
        String version = pluginRequest.getVersion();
        if (version != null) {
            PluginRequest alreadyLoadedRequest = alreadyLoadedPluginRequest(parentLoaderScope, pluginId);
            if (alreadyLoadedRequest == null) {
                throw new InvalidPluginRequestException(
                    pluginRequest,
                    "Plugins with unknown version (e.g. from 'buildSrc' or TestKit injected classpath) cannot be requested with a version");
            }
            if (!version.equals(alreadyLoadedRequest.getVersion())) {
                throw new InvalidPluginRequestException(
                    pluginRequest,
                    "Cannot apply version " + version + " of '" + pluginId + "' as version " + alreadyLoadedRequest.getVersion() + " is already on the classpath");
            }
        }
    }

    @Nullable
    private PluginRequest alreadyLoadedPluginRequest(ClassLoaderScope loaderScope, PluginId pluginId) {
        List<PluginRequest> loadedRequests = loaderScope.getMetaInfo(PluginRequest.class);
        for (PluginRequest loadedRequest : loadedRequests) {
            if (pluginId.equals(loadedRequest.getId())) {
                return loadedRequest;
            }
        }
        ClassLoaderScope parentScope = loaderScope.getParent();
        if (parentScope != loaderScope) {
            return alreadyLoadedPluginRequest(parentScope, pluginId);
        }
        return null;
    }

    private boolean isAbsentFromTheClasspath(PluginId pluginId) {
        return pluginDescriptorLocator.findPluginDescriptor(pluginId.toString()) == null;
    }

    private boolean isCorePlugin(PluginId pluginId) {
        return corePluginRegistry.lookup(pluginId) != null;
    }
}
