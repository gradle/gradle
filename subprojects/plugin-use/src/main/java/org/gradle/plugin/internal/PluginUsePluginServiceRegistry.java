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

import org.gradle.StartParameter;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.artifacts.DependencyManagementServices;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.artifacts.dsl.dependencies.UnknownProjectFinder;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.initialization.RootScriptDomainObjectContext;
import org.gradle.api.internal.plugins.PluginInspector;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.initialization.ClassLoaderScopeRegistry;
import org.gradle.initialization.layout.BuildLayout;
import org.gradle.initialization.layout.BuildLayoutConfiguration;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.internal.Factory;
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
import org.gradle.plugin.use.internal.PluginRequestApplicator;
import org.gradle.plugin.use.internal.PluginResolverFactory;
import org.gradle.plugin.use.resolve.service.internal.InjectedClasspathPluginResolver;

public class PluginUsePluginServiceRegistry extends AbstractPluginServiceRegistry {

    public void registerBuildServices(ServiceRegistration registration) {
        registration.addProvider(new BuildScopeServices());
    }

    @Override
    public void registerBuildSessionServices(ServiceRegistration registration) {
        registration.addProvider(new BuildSessionScopeServices());
    }

    @Override
    public void registerSettingsServices(ServiceRegistration registration) {
        registration.addProvider(new SettingsScopeServices());
    }

    private static class SettingsScopeServices {

        protected PluginManagementSpec createPluginManagementSpec(Instantiator instantiator, PluginDependencyResolutionServices dependencyResolutionServices,
                                                                  PluginResolutionStrategyInternal internalPluginResolutionStrategy) {
            return instantiator.newInstance(DefaultPluginManagementSpec.class, dependencyResolutionServices.getPluginRepositoryHandlerProvider(), internalPluginResolutionStrategy);
        }
    }

    private static class BuildSessionScopeServices {

        AutoAppliedPluginRegistry createAutoAppliedPluginRegistry(StartParameter startParameter) {
            return new DefaultAutoAppliedPluginRegistry(startParameter);
        }

        AutoAppliedPluginHandler createAutoAppliedPluginHandler(AutoAppliedPluginRegistry registry) {
            return new DefaultAutoAppliedPluginHandler(registry);
        }
    }

    private static class BuildScopeServices {

        PluginResolverFactory createPluginResolverFactory(PluginRegistry pluginRegistry, DocumentationRegistry documentationRegistry,
                                                          InjectedClasspathPluginResolver injectedClasspathPluginResolver,
                                                          PluginDependencyResolutionServices dependencyResolutionServices, VersionSelectorScheme versionSelectorScheme) {
            return new PluginResolverFactory(pluginRegistry, documentationRegistry, injectedClasspathPluginResolver, dependencyResolutionServices, versionSelectorScheme);
        }

        PluginRequestApplicator createPluginRequestApplicator(PluginRegistry pluginRegistry, PluginDependencyResolutionServices dependencyResolutionServices,
                                                              PluginResolverFactory pluginResolverFactory, PluginResolutionStrategyInternal internalPluginResolutionStrategy,
                                                              PluginInspector pluginInspector, CachedClasspathTransformer cachedClasspathTransformer) {
            return new DefaultPluginRequestApplicator(
                pluginRegistry, pluginResolverFactory, dependencyResolutionServices.getPluginRepositoriesProvider(),
                internalPluginResolutionStrategy, pluginInspector, cachedClasspathTransformer);
        }

        InjectedClasspathPluginResolver createInjectedClassPathPluginResolver(ClassLoaderScopeRegistry classLoaderScopeRegistry, PluginInspector pluginInspector,
                                                                              InjectedPluginClasspath injectedPluginClasspath) {
            return new InjectedClasspathPluginResolver(classLoaderScopeRegistry.getCoreAndPluginsScope(), pluginInspector, injectedPluginClasspath.getClasspath());
        }

        PluginResolutionStrategyInternal createPluginResolutionStrategy(Instantiator instantiator, ListenerManager listenerManager) {
            return instantiator.newInstance(DefaultPluginResolutionStrategy.class, listenerManager);
        }

        PluginDependencyResolutionServices createPluginDependencyResolutionServices(StartParameter startParameter, BuildLayoutFactory buildLayoutFactory, FileResolver fileResolver,
                                                                                    DependencyManagementServices dependencyManagementServices, DependencyMetaDataProvider dependencyMetaDataProvider) {
            return new PluginDependencyResolutionServices(
                makeDependencyResolutionServicesFactory(buildLayoutFactory, startParameter, fileResolver, dependencyManagementServices, dependencyMetaDataProvider));
        }

        private Factory<DependencyResolutionServices> makeDependencyResolutionServicesFactory(final BuildLayoutFactory buildLayoutFactory, final StartParameter startParameter,
                                                                                              final FileResolver fileResolver, final DependencyManagementServices dependencyManagementServices,
                                                                                              final DependencyMetaDataProvider dependencyMetaDataProvider) {
            return new Factory<DependencyResolutionServices>() {
                @Override
                public DependencyResolutionServices create() {
                    BuildLayout buildLayout = buildLayoutFactory.getLayoutFor(new BuildLayoutConfiguration(startParameter));
                    FileResolver capableFileResolver = fileResolver.newResolver(buildLayout.getSettingsDir());
                    return dependencyManagementServices.create(capableFileResolver, dependencyMetaDataProvider, makeUnknownProjectFinder(), RootScriptDomainObjectContext.INSTANCE);
                }
            };
        }

        private ProjectFinder makeUnknownProjectFinder() {
            return new UnknownProjectFinder("Cannot use project dependencies in a plugin resolution definition.");
        }
    }
}
