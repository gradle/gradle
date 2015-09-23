/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.plugin.use.resolve.service.internal;

import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.plugins.DefaultPluginRegistry;
import org.gradle.api.internal.plugins.PluginImplementation;
import org.gradle.api.internal.plugins.PluginInspector;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.api.plugins.UnknownPluginException;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.plugin.internal.PluginId;
import org.gradle.plugin.use.internal.InvalidPluginRequestException;
import org.gradle.plugin.use.internal.PluginRequest;
import org.gradle.plugin.use.resolve.internal.PluginResolution;
import org.gradle.plugin.use.resolve.internal.PluginResolutionResult;
import org.gradle.plugin.use.resolve.internal.PluginResolveContext;
import org.gradle.plugin.use.resolve.internal.PluginResolver;

public class InjectedClasspathPluginResolver implements PluginResolver {
    private final ClassLoaderScope parentScope;
    private final ClassPath classpath;
    private final PluginRegistry pluginRegistry;

    public InjectedClasspathPluginResolver(ClassLoaderScope parentScope, PluginInspector pluginInspector, ClassPath injectedClasspath) {
        this.parentScope = parentScope;
        this.classpath = injectedClasspath;
        this.pluginRegistry = new DefaultPluginRegistry(pluginInspector, createClassLoaderScope());
    }

    private ClassLoaderScope createClassLoaderScope() {
        ClassLoaderScope loaderScope = parentScope.createChild("injected-plugin");
        loaderScope.local(classpath);
        loaderScope.lock();
        return loaderScope;
    }

    public void resolve(PluginRequest pluginRequest, PluginResolutionResult result) throws InvalidPluginRequestException {
        PluginImplementation<?> plugin = pluginRegistry.lookup(pluginRequest.getId());

        if (plugin != null) {
            PluginResolution resolution = new InjectedClasspathPluginResolution(plugin);
            result.found(getDescription(), resolution);
        } else {
            throw new UnknownPluginException("Plugin with id '" + pluginRequest.getId() + "' not found. Searched classpath: " + classpath.getAsFiles());
        }
    }

    public String getDescription() {
        return "Injected classpath";
    }

    public boolean isClasspathEmpty() {
        return classpath.isEmpty();
    }

    private class InjectedClasspathPluginResolution implements PluginResolution {
        private final PluginImplementation<?> plugin;

        public InjectedClasspathPluginResolution(PluginImplementation<?> plugin) {
            this.plugin = plugin;
        }

        public PluginId getPluginId() {
            return plugin.getPluginId();
        }

        public void execute(PluginResolveContext pluginResolveContext) {
            pluginResolveContext.add(plugin);
            pluginResolveContext.addClassPath(classpath);
        }
    }
}
