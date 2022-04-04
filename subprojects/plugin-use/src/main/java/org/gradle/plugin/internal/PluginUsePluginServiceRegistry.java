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

package org.gradle.plugin.internal;

import org.gradle.api.internal.BuildDefinition;
import org.gradle.api.internal.artifacts.DependencyManagementServices;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.artifacts.dsl.dependencies.UnknownProjectFinder;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.initialization.RootScriptDomainObjectContext;
import org.gradle.api.internal.plugins.PluginInspector;
import org.gradle.initialization.ClassLoaderScopeRegistry;
import org.gradle.internal.Factory;
import org.gradle.internal.build.BuildIncluder;
import org.gradle.internal.classpath.CachedClasspathTransformer;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry;
import org.gradle.plugin.management.PluginManagementSpec;
import org.gradle.plugin.management.internal.DefaultPluginManagementSpec;
import org.gradle.plugin.management.internal.DefaultPluginResolutionStrategy;
import org.gradle.plugin.management.internal.PluginResolutionStrategyInternal;
import org.gradle.plugin.management.internal.autoapply.AutoAppliedPluginHandler;
import org.gradle.plugin.management.internal.autoapply.AutoAppliedPluginRegistry;
import org.gradle.plugin.management.internal.autoapply.DefaultAutoAppliedPluginHandler;
import org.gradle.plugin.management.internal.autoapply.DefaultAutoAppliedPluginRegistry;
import org.gradle.plugin.use.internal.DefaultPluginRequestApplicator;
import org.gradle.plugin.use.internal.InjectedPluginClasspath;
import org.gradle.plugin.use.internal.PluginDependencyResolutionServices;
import org.gradle.plugin.use.internal.PluginRepositoryHandlerProvider;
import org.gradle.plugin.use.internal.PluginResolverFactory;
import org.gradle.plugin.use.resolve.service.internal.ClientInjectedClasspathPluginResolver;
import org.gradle.plugin.use.resolve.service.internal.DefaultInjectedClasspathPluginResolver;
import org.gradle.plugin.use.resolve.service.internal.InjectedClasspathInstrumentationStrategy;
import org.gradle.plugin.use.tracker.internal.PluginVersionTracker;

public class PluginUsePluginServiceRegistry extends AbstractPluginServiceRegistry {

    @Override
    public void registerBuildServices(ServiceRegistration registration) {
        registration.addProvider(new BuildScopeServices());
    }

    @Override
    public void registerSettingsServices(ServiceRegistration registration) {
        registration.addProvider(new SettingsScopeServices());
    }

    private static class SettingsScopeServices {

        protected PluginManagementSpec createPluginManagementSpec(
            Instantiator instantiator,
            PluginRepositoryHandlerProvider pluginRepositoryHandlerProvider,
            PluginResolutionStrategyInternal internalPluginResolutionStrategy,
            FileResolver fileResolver,
            BuildIncluder buildIncluder
        ) {
            return instantiator.newInstance(DefaultPluginManagementSpec.class, pluginRepositoryHandlerProvider, internalPluginResolutionStrategy, fileResolver, buildIncluder);
        }
    }

    private static class BuildScopeServices {
        void configure(ServiceRegistration registration) {
            registration.add(PluginResolverFactory.class);
            registration.add(DefaultPluginRequestApplicator.class);
            registration.add(PluginVersionTracker.class);
        }

        AutoAppliedPluginRegistry createAutoAppliedPluginRegistry(BuildDefinition buildDefinition) {
            return new DefaultAutoAppliedPluginRegistry(buildDefinition);
        }

        AutoAppliedPluginHandler createAutoAppliedPluginHandler(AutoAppliedPluginRegistry registry) {
            return new DefaultAutoAppliedPluginHandler(registry);
        }

        ClientInjectedClasspathPluginResolver createInjectedClassPathPluginResolver(
            ClassLoaderScopeRegistry classLoaderScopeRegistry, PluginInspector pluginInspector,
            InjectedPluginClasspath injectedPluginClasspath, CachedClasspathTransformer classpathTransformer,
            InjectedClasspathInstrumentationStrategy instrumentationStrategy
        ) {
            if (injectedPluginClasspath.getClasspath().isEmpty()) {
                return ClientInjectedClasspathPluginResolver.EMPTY;
            }
            return new DefaultInjectedClasspathPluginResolver(classLoaderScopeRegistry.getCoreAndPluginsScope(), classpathTransformer, pluginInspector, injectedPluginClasspath.getClasspath(), instrumentationStrategy);
        }

        PluginResolutionStrategyInternal createPluginResolutionStrategy(Instantiator instantiator, ListenerManager listenerManager) {
            return instantiator.newInstance(DefaultPluginResolutionStrategy.class, listenerManager);
        }

        PluginDependencyResolutionServices createPluginDependencyResolutionServices(
            FileResolver fileResolver, FileCollectionFactory fileCollectionFactory,
            DependencyManagementServices dependencyManagementServices, DependencyMetaDataProvider dependencyMetaDataProvider
        ) {
            return new PluginDependencyResolutionServices(
                makeDependencyResolutionServicesFactory(fileResolver, fileCollectionFactory, dependencyManagementServices, dependencyMetaDataProvider));
        }

        private Factory<DependencyResolutionServices> makeDependencyResolutionServicesFactory(
            final FileResolver fileResolver,
            final FileCollectionFactory fileCollectionFactory,
            final DependencyManagementServices dependencyManagementServices,
            final DependencyMetaDataProvider dependencyMetaDataProvider
        ) {
            return new Factory<DependencyResolutionServices>() {
                @Override
                public DependencyResolutionServices create() {
                    return dependencyManagementServices.create(fileResolver, fileCollectionFactory, dependencyMetaDataProvider, makeUnknownProjectFinder(), RootScriptDomainObjectContext.PLUGINS);
                }
            };
        }

        private ProjectFinder makeUnknownProjectFinder() {
            return new UnknownProjectFinder("Cannot use project dependencies in a plugin resolution definition.");
        }
    }
}
