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

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.initialization.ScriptClassPathResolutionContext;
import org.gradle.api.internal.initialization.ScriptClassPathResolver;
import org.gradle.api.internal.plugins.DefaultPluginRegistry;
import org.gradle.api.internal.plugins.PluginImplementation;
import org.gradle.api.internal.plugins.PluginInspector;
import org.gradle.api.internal.plugins.PluginManagerInternal;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.internal.Factory;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.lazy.Lazy;
import org.gradle.plugin.management.internal.PluginRequestInternal;
import org.gradle.plugin.use.PluginId;
import org.gradle.plugin.use.resolve.internal.PluginResolution;
import org.gradle.plugin.use.resolve.internal.PluginResolutionResult;
import org.gradle.plugin.use.resolve.internal.PluginResolutionVisitor;
import org.gradle.plugin.use.resolve.internal.PluginResolver;
import org.gradle.plugin.use.resolve.service.internal.InjectedClasspathInstrumentationStrategy.TransformMode;

import java.io.File;
import java.util.Collection;
import java.util.stream.Collectors;

public class DefaultInjectedClasspathPluginResolver implements ClientInjectedClasspathPluginResolver, PluginResolver {

    private final ClassPath injectedClasspath;
    private final FileCollectionFactory fileCollectionFactory;
    private final ScriptClassPathResolver scriptClassPathResolver;
    private final ClassLoaderScope parentScope;
    private final PluginInspector pluginInspector;
    private final Lazy<PluginRegistry> pluginRegistry;

    public DefaultInjectedClasspathPluginResolver(
        ClassLoaderScope parentScope,
        ScriptClassPathResolver scriptClassPathResolver,
        FileCollectionFactory fileCollectionFactory,
        PluginInspector pluginInspector,
        ClassPath injectedClasspath,
        InjectedClasspathInstrumentationStrategy instrumentationStrategy,
        Factory<DependencyResolutionServices> dependencyResolutionServicesFactory
    ) {
        this.parentScope = parentScope;
        this.pluginInspector = pluginInspector;
        this.injectedClasspath = injectedClasspath;
        this.fileCollectionFactory = fileCollectionFactory;
        this.scriptClassPathResolver = scriptClassPathResolver;
        this.pluginRegistry = createPluginRegistry(instrumentationStrategy, dependencyResolutionServicesFactory);
    }

    private Lazy<PluginRegistry> createPluginRegistry(
        InjectedClasspathInstrumentationStrategy instrumentationStrategy,
        Factory<DependencyResolutionServices> dependencyResolutionServicesFactory
    ) {

        // One wanted side effect of calling InstrumentationStrategy.getTransform() is also to report
        // a configuration cache problem if third-party agent is used with TestKit with configuration cache,
        // see ConfigurationCacheInjectedClasspathInstrumentationStrategy implementation.
        TransformMode transform = instrumentationStrategy.getTransform();
        switch (transform) {
            case NONE:
                return Lazy.locking().of(this::createUninstrumentedPluginRegistry);
            case BUILD_LOGIC:
                return Lazy.locking().of(() -> createInstrumentedPluginRegistry(dependencyResolutionServicesFactory));
            default:
                throw new IllegalArgumentException("Unknown instrumentation strategy: " + transform);
        }
    }

    private PluginRegistry createUninstrumentedPluginRegistry() {
        return newPluginRegistryOf(injectedClasspath);
    }

    private PluginRegistry createInstrumentedPluginRegistry(Factory<DependencyResolutionServices> dependencyResolutionServicesFactory) {
        DependencyResolutionServices dependencyResolutionServices = dependencyResolutionServicesFactory.create();
        DependencyHandler dependencies = dependencyResolutionServices.getDependencyHandler();
        ConfigurationContainer configurations = dependencyResolutionServices.getConfigurationContainer();
        Dependency injectedClasspathDependency = dependencies.create(fileCollectionFactory.fixed(injectedClasspath.getAsFiles()));
        Configuration configuration = configurations.detachedConfiguration(injectedClasspathDependency);
        ScriptClassPathResolutionContext resolutionContext = scriptClassPathResolver.prepareDependencyHandler(dependencies);
        scriptClassPathResolver.prepareClassPath(configuration, resolutionContext);
        ClassPath instrumentedClassPath = scriptClassPathResolver.resolveClassPath(configuration, resolutionContext);
        return newPluginRegistryOf(instrumentedClassPath);
    }

    private PluginRegistry newPluginRegistryOf(ClassPath classPath) {
        return new DefaultPluginRegistry(pluginInspector,
            parentScope.createChild("injected-plugin", null)
                .local(classPath)
                .lock()
        );
    }

    @Override
    public void collectResolversInto(Collection<? super PluginResolver> dest) {
        dest.add(this);
    }

    @Override
    public PluginResolutionResult resolve(PluginRequestInternal pluginRequest) {
        PluginImplementation<?> plugin = pluginRegistry.get().lookup(pluginRequest.getId());
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
