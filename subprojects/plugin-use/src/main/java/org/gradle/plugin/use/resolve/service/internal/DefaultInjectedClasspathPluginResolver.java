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

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.plugins.DefaultPluginRegistry;
import org.gradle.api.internal.plugins.PluginImplementation;
import org.gradle.api.internal.plugins.PluginInspector;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.internal.classpath.CachedClasspathTransformer;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.TransformedClassPath;
import org.gradle.plugin.management.internal.InvalidPluginRequestException;
import org.gradle.plugin.management.internal.PluginRequestInternal;
import org.gradle.plugin.use.PluginId;
import org.gradle.plugin.use.resolve.internal.PluginResolution;
import org.gradle.plugin.use.resolve.internal.PluginResolutionResult;
import org.gradle.plugin.use.resolve.internal.PluginResolveContext;
import org.gradle.plugin.use.resolve.internal.PluginResolver;

import java.io.File;
import java.util.Collection;

public class DefaultInjectedClasspathPluginResolver implements ClientInjectedClasspathPluginResolver, PluginResolver {

    private final ClassPath injectedClasspath;
    private final PluginRegistry pluginRegistry;

    public DefaultInjectedClasspathPluginResolver(ClassLoaderScope parentScope, CachedClasspathTransformer classpathTransformer, PluginInspector pluginInspector, ClassPath injectedClasspath, InjectedClasspathInstrumentationStrategy instrumentationStrategy) {
        this.injectedClasspath = injectedClasspath;
        ClassPath cachedClassPath = TransformedClassPath.handleInstrumentingArtifactTransform(classpathTransformer.transform(injectedClasspath, instrumentationStrategy.getTransform()));
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
    public void resolve(PluginRequestInternal pluginRequest, PluginResolutionResult result) throws InvalidPluginRequestException {
        PluginImplementation<?> plugin = pluginRegistry.lookup(pluginRequest.getId());
        if (plugin == null) {
            String classpathStr = Joiner.on(File.pathSeparator).join(Iterables.transform(injectedClasspath.getAsFiles(), new Function<File, String>() {
                @Override
                public String apply(File input) {
                    return input.getAbsolutePath();
                }
            }));
            result.notFound(getDescription(), "classpath: " + classpathStr);
        } else {
            result.found(getDescription(), new InjectedClasspathPluginResolution(plugin));
        }
    }

    public String getDescription() {
        // It's true right now that this is always coming from the TestKit, but might not be in the future.
        return "Gradle TestKit";
    }

    public boolean isClasspathEmpty() {
        return injectedClasspath.isEmpty();
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
        public void execute(PluginResolveContext pluginResolveContext) {
            pluginResolveContext.addFromDifferentLoader(plugin);
        }
    }
}
