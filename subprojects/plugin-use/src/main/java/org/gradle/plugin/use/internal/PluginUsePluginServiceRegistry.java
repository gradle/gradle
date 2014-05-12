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
import org.gradle.api.internal.artifacts.repositories.DefaultPasswordCredentials;
import org.gradle.internal.resource.transport.http.DefaultHttpSettings;
import org.gradle.internal.resource.transport.http.HttpClientHelper;
import org.gradle.internal.resource.transport.http.HttpResourceAccessor;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.initialization.BasicDomainObjectContext;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.Factory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.PluginServiceRegistry;
import org.gradle.plugin.resolve.internal.PluginResolverFactory;
import org.gradle.plugin.use.resolve.portal.internal.PluginPortalClient;
import org.gradle.plugin.use.resolve.portal.internal.PluginPortalResolver;

public class PluginUsePluginServiceRegistry implements PluginServiceRegistry {


    public void registerGlobalServices(ServiceRegistration registration) {
    }

    public void registerBuildServices(ServiceRegistration registration) {
        registration.addProvider(new BuildScopeServices());
    }

    public void registerProjectServices(ServiceRegistration registration) {
    }

    private static class BuildScopeServices {
        PluginPortalClient createPluginPortalClient() {
            HttpClientHelper http = new HttpClientHelper(new DefaultHttpSettings(new DefaultPasswordCredentials()));
            HttpResourceAccessor accessor = new HttpResourceAccessor(http);
            return new PluginPortalClient(accessor);
        }

        PluginPortalResolver createPluginPortalResolver(PluginPortalClient pluginPortalClient, Instantiator instantiator, StartParameter startParameter, final DependencyManagementServices dependencyManagementServices, final FileResolver fileResolver, final DependencyMetaDataProvider dependencyMetaDataProvider) {
            final ProjectFinder projectFinder = new ProjectFinder() {
                public ProjectInternal getProject(String path) {
                    throw new UnknownProjectException("Cannot use project dependencies in a plugin resolution definition.");
                }
            };

            return new PluginPortalResolver(pluginPortalClient, instantiator, startParameter, new Factory<DependencyResolutionServices>() {
                public DependencyResolutionServices create() {
                    return dependencyManagementServices.create(fileResolver, dependencyMetaDataProvider, projectFinder, new BasicDomainObjectContext());
                }
            });
        }

        PluginResolverFactory createPluginResolverFactory(PluginRegistry pluginRegistry, DocumentationRegistry documentationRegistry, PluginPortalResolver pluginPortalResolver) {
            return new PluginResolverFactory(pluginRegistry, documentationRegistry, pluginPortalResolver);
        }

        PluginRequestApplicatorFactory createPluginUseServices(PluginResolverFactory pluginResolverFactory) {
            return new DefaultPluginRequestApplicatorFactory(pluginResolverFactory);
        }
    }
}
