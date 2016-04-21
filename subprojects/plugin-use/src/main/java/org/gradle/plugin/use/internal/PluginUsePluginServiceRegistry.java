/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.plugin.use.internal;

import org.gradle.StartParameter;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.artifacts.DependencyManagementServices;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.artifacts.dsl.dependencies.UnknownProjectFinder;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.api.internal.file.FileLookup;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.initialization.BasicDomainObjectContext;
import org.gradle.api.internal.plugins.PluginInspector;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.initialization.ClassLoaderScopeRegistry;
import org.gradle.internal.Factory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resource.transport.http.SslContextFactory;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.PluginServiceRegistry;
import org.gradle.plugin.use.resolve.service.internal.*;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class PluginUsePluginServiceRegistry implements PluginServiceRegistry {

    public static final String CACHE_NAME = "plugin-resolution";

    public void registerGlobalServices(ServiceRegistration registration) {
    }

    public void registerBuildSessionServices(ServiceRegistration registration) {
    }

    public void registerBuildServices(ServiceRegistration registration) {
        registration.addProvider(new BuildScopeServices());
    }

    public void registerGradleServices(ServiceRegistration registration) {
    }

    public void registerProjectServices(ServiceRegistration registration) {
    }

    private static class BuildScopeServices {
        PluginResolutionServiceClient createPluginResolutionServiceClient(CacheRepository cacheRepository, StartParameter startParameter, SslContextFactory sslContextFactory) {
            PluginResolutionServiceClient httpClient = startParameter.isOffline()
                ? new OfflinePluginResolutionServiceClient()
                : new HttpPluginResolutionServiceClient(sslContextFactory);

            PersistentCache cache = cacheRepository
                .cache(CACHE_NAME)
                .withDisplayName("Plugin Resolution Cache")
                .withLockOptions(mode(FileLockManager.LockMode.None))
                .open();

            PluginResolutionServiceClient persistentCachingClient = new PersistentCachingPluginResolutionServiceClient(httpClient, cache);
            PluginResolutionServiceClient inMemoryCachingClient = new InMemoryCachingPluginResolutionServiceClient(persistentCachingClient);
            return new DeprecationListeningPluginResolutionServiceClient(inMemoryCachingClient);
        }

        PluginResolutionServiceResolver createPluginResolutionServiceResolver(PluginResolutionServiceClient pluginResolutionServiceClient, VersionSelectorScheme versionSelectorScheme,
                                                                              StartParameter startParameter, final DependencyManagementServices dependencyManagementServices,
                                                                              final FileResolver fileResolver, final DependencyMetaDataProvider dependencyMetaDataProvider,
                                                                              ClassLoaderScopeRegistry classLoaderScopeRegistry, PluginInspector pluginInspector) {
            final Factory<DependencyResolutionServices> dependencyResolutionServicesFactory = makeDependencyResolutionServicesFactory(dependencyManagementServices, fileResolver, dependencyMetaDataProvider);
            return new PluginResolutionServiceResolver(pluginResolutionServiceClient, versionSelectorScheme, startParameter, classLoaderScopeRegistry.getCoreScope(), dependencyResolutionServicesFactory, pluginInspector);
        }

        PluginResolverFactory createPluginResolverFactory(PluginRegistry pluginRegistry, DocumentationRegistry documentationRegistry, PluginResolutionServiceResolver pluginResolutionServiceResolver,
                                                          DefaultPluginRepositoryHandler pluginRepositoryHandler, InjectedClasspathPluginResolver injectedClasspathPluginResolver, FileLookup fileLookup) {
            return new PluginResolverFactory(pluginRegistry, documentationRegistry, pluginResolutionServiceResolver, pluginRepositoryHandler, injectedClasspathPluginResolver);
        }

        PluginRequestApplicator createPluginRequestApplicator(PluginRegistry pluginRegistry, PluginResolverFactory pluginResolverFactory) {
            return new DefaultPluginRequestApplicator(pluginRegistry, pluginResolverFactory);
        }

        InjectedClasspathPluginResolver createInjectedClassPathPluginResolver(ClassLoaderScopeRegistry classLoaderScopeRegistry, PluginInspector pluginInspector, InjectedPluginClasspath injectedPluginClasspath) {
            return new InjectedClasspathPluginResolver(classLoaderScopeRegistry.getCoreAndPluginsScope(), pluginInspector, injectedPluginClasspath.getClasspath());
        }

        DefaultPluginRepositoryHandler createPluginRepositoryHandler(VersionSelectorScheme versionSelectorScheme,
                                                                     final DependencyManagementServices dependencyManagementServices, final FileResolver fileResolver,
                                                                     final DependencyMetaDataProvider dependencyMetaDataProvider, Instantiator instantiator) {

            final Factory<DependencyResolutionServices> dependencyResolutionServicesFactory = makeDependencyResolutionServicesFactory(dependencyManagementServices, fileResolver, dependencyMetaDataProvider);
            return instantiator.newInstance(DefaultPluginRepositoryHandler.class, fileResolver, dependencyResolutionServicesFactory, versionSelectorScheme, instantiator);
        }

        private Factory<DependencyResolutionServices> makeDependencyResolutionServicesFactory(final DependencyManagementServices dependencyManagementServices, final FileResolver fileResolver, final DependencyMetaDataProvider dependencyMetaDataProvider) {
            return new Factory<DependencyResolutionServices>() {
                public DependencyResolutionServices create() {
                    return dependencyManagementServices.create(fileResolver, dependencyMetaDataProvider, makeUnknownProjectFinder(), new BasicDomainObjectContext());
                }
            };
        }

        private ProjectFinder makeUnknownProjectFinder() {
            return new UnknownProjectFinder("Cannot use project dependencies in a plugin resolution definition.");
        }
    }
}
