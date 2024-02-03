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
import org.gradle.api.internal.plugins.PluginManagerInternal;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.internal.classpath.CachedClasspathTransformer;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.plugin.management.internal.PluginRequestInternal;
import org.gradle.plugin.use.PluginId;
import org.gradle.plugin.use.resolve.internal.PluginResolution;
import org.gradle.plugin.use.resolve.internal.PluginResolutionResult;
import org.gradle.plugin.use.resolve.internal.PluginResolutionVisitor;
import org.gradle.plugin.use.resolve.internal.PluginResolver;

import java.io.File;
import java.util.Collection;
import java.util.stream.Collectors;

public class DefaultInjectedClasspathPluginResolver implements ClientInjectedClasspathPluginResolver, PluginResolver {

    private final ClassPath injectedClasspath;
    private final PluginRegistry pluginRegistry;

    public DefaultInjectedClasspathPluginResolver(ClassLoaderScope parentScope, CachedClasspathTransformer classpathTransformer, PluginInspector pluginInspector, ClassPath injectedClasspath, InjectedClasspathInstrumentationStrategy instrumentationStrategy) {
        this.injectedClasspath = injectedClasspath;
        ClassPath cachedClassPath = classpathTransformer.transform(injectedClasspath, instrumentationStrategy.getTransform());
        this.pluginRegistry = new DefaultPluginRegistry(pluginInspector,
            parentScope.createChild("injected-plugin", null)
                .local(cachedClassPath)
                .lock()
        );
    }

    @Override
    public void collectResolversInto(Collection<? super PluginResolver> dest) {
        dest.add(this);
    }

    @Override
    public PluginResolutionResult resolve(PluginRequestInternal pluginRequest) {
        PluginImplementation<?> plugin = pluginRegistry.lookup(pluginRequest.getId());
        if (plugin == null) {
            String classpathStr = injectedClasspath.getAsFiles().stream().map(File::getAbsolutePath).collect(Collectors.joining(File.pathSeparator));
            return PluginResolutionResult.notFound(getDescription(), "classpath: " + classpathStr);
        } else {
            return PluginResolutionResult.found(new InjectedClasspathPluginResolution(plugin));
        }
    }

    public String getDescription() {
        // It's true right now that this is always coming from the TestKit, but might not be in the future.
        return "Gradle TestKit";
    }

    private static class InjectedClasspathPluginResolution implements PluginResolution {
        private final PluginImplementation<?> plugin;

        public InjectedClasspathPluginResolution(PluginImplementation<?> plugin) {
            this.plugin = plugin;
        }

        @Override
        public PluginId getPluginId() {
            return plugin.getPluginId();
        }

        @Override
        public void accept(PluginResolutionVisitor visitor) {
            visitor.visitClassLoader(plugin.asClass().getClassLoader());
        }

        @Override
        public void applyTo(PluginManagerInternal pluginManager) {
            pluginManager.apply(plugin);
        }
    }
}
