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
import org.gradle.api.UnknownProjectException;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.artifacts.DependencyManagementServices;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionMatcher;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.initialization.BasicDomainObjectContext;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.initialization.ClassLoaderScopeRegistry;
import org.gradle.internal.Factory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resource.PasswordCredentials;
import org.gradle.internal.resource.transport.http.DefaultHttpSettings;
import org.gradle.internal.resource.transport.http.HttpClientHelper;
import org.gradle.internal.resource.transport.http.HttpResourceAccessor;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.PluginServiceRegistry;
import org.gradle.plugin.use.resolve.service.internal.*;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class PluginUsePluginServiceRegistry implements PluginServiceRegistry {

    public static final String CACHE_NAME = "plugin-resolution";

    public void registerGlobalServices(ServiceRegistration registration) {
    }

    public void registerBuildServices(ServiceRegistration registration) {
        registration.addProvider(new BuildScopeServices());
    }

    public void registerProjectServices(ServiceRegistration registration) {
    }

    private static class BuildScopeServices {
        PluginResolutionServiceClient createPluginResolutionServiceClient(CacheRepository cacheRepository, StartParameter startParameter) {
            HttpClientHelper http = new HttpClientHelper(new DefaultHttpSettings(new PasswordCredentials()));
            HttpResourceAccessor accessor = new HttpResourceAccessor(http);
            PluginResolutionServiceClient httpClient = startParameter.isOffline()
                    ? new OfflinePluginResolutionServiceClient()
                    : new HttpPluginResolutionServiceClient(accessor);

            PersistentCache cache = cacheRepository
                    .cache(CACHE_NAME)
                    .withDisplayName("Plugin Resolution Cache")
                    .withLockOptions(mode(FileLockManager.LockMode.None))
                    .open();

            PluginResolutionServiceClient persistentCachingClient = new PersistentCachingPluginResolutionServiceClient(httpClient, cache);
            PluginResolutionServiceClient inMemoryCachingClient = new InMemoryCachingPluginResolutionServiceClient(persistentCachingClient);
            return new DeprecationListeningPluginResolutionServiceClient(inMemoryCachingClient);
        }

        PluginResolutionServiceResolver createPluginResolutionServiceResolver(PluginResolutionServiceClient pluginResolutionServiceClient, Instantiator instantiator, VersionMatcher versionMatcher, StartParameter startParameter, final DependencyManagementServices dependencyManagementServices, final FileResolver fileResolver, final DependencyMetaDataProvider dependencyMetaDataProvider, ClassLoaderScopeRegistry classLoaderScopeRegistry) {
            final ProjectFinder projectFinder = new ProjectFinder() {
                public ProjectInternal getProject(String path) {
                    throw new UnknownProjectException("Cannot use project dependencies in a plugin resolution definition.");
                }
            };

            return new PluginResolutionServiceResolver(pluginResolutionServiceClient, instantiator, versionMatcher, startParameter, classLoaderScopeRegistry.getCoreScope(), new Factory<DependencyResolutionServices>() {
                public DependencyResolutionServices create() {
                    return dependencyManagementServices.create(fileResolver, dependencyMetaDataProvider, projectFinder, new BasicDomainObjectContext());
                }
            });
        }

        PluginResolverFactory createPluginResolverFactory(PluginRegistry pluginRegistry, DocumentationRegistry documentationRegistry, PluginResolutionServiceResolver pluginResolutionServiceResolver) {
            return new PluginResolverFactory(pluginRegistry, documentationRegistry, pluginResolutionServiceResolver);
        }

        PluginRequestApplicator createPluginRequestApplicator(PluginRegistry pluginRegistry, PluginResolverFactory pluginResolverFactory) {
            return new DefaultPluginRequestApplicator(pluginRegistry, pluginResolverFactory.create());
        }
    }
}
