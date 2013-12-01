/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.api.UnknownProjectException;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.artifacts.DependencyManagementServices;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.initialization.ScriptClassLoaderProvider;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.PluginAware;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.plugin.resolve.internal.AndroidPluginMapper;
import org.gradle.plugin.resolve.internal.ModuleMappingPluginResolver;
import org.gradle.plugin.resolve.internal.PluginRegistryPluginResolver;
import org.gradle.plugin.resolve.internal.TomcatPluginMapper;

public class DefaultPluginHandlerFactory implements PluginHandlerFactory {

    private final PluginRegistry pluginRegistry;
    private final Instantiator instantiator;
    private final DependencyManagementServices dependencyManagementServices;
    private final FileResolver fileResolver;
    private final DependencyMetaDataProvider dependencyMetaDataProvider;
    private final ClassLoader parentClassLoader;

    private final ProjectFinder projectFinder = new ProjectFinder() {
        public ProjectInternal getProject(String path) {
            throw new UnknownProjectException("Cannot use project dependencies in a plugin resolution definition.");
        }
    };

    public DefaultPluginHandlerFactory(PluginRegistry pluginRegistry, Instantiator instantiator, DependencyManagementServices dependencyManagementServices, FileResolver fileResolver, DependencyMetaDataProvider dependencyMetaDataProvider, ClassLoader parentClassLoader) {
        this.pluginRegistry = pluginRegistry;
        this.instantiator = instantiator;
        this.dependencyManagementServices = dependencyManagementServices;
        this.fileResolver = fileResolver;
        this.dependencyMetaDataProvider = dependencyMetaDataProvider;
        this.parentClassLoader = parentClassLoader;
    }

    public PluginHandlerInternal createPluginHandler(final Object target, ScriptClassLoaderProvider classLoaderProvider) {
        if (target instanceof PluginAware) {
            PluginHandlerInternal pluginHandler = new DefaultPluginHandler(instantiator, new PluginResolutionApplicator((PluginAware) target, parentClassLoader));
            addDefaultResolvers(pluginHandler, classLoaderProvider);
            return pluginHandler;
        } else {
            return new NonPluggableTargetPluginHandler(target);
        }
    }

    private void addDefaultResolvers(PluginHandlerInternal pluginHandler, ScriptClassLoaderProvider classLoaderProvider) {
        pluginHandler.getResolvers().add(new PluginRegistryPluginResolver(pluginRegistry));
        pluginHandler.getResolvers().add(new ModuleMappingPluginResolver("android plugin resolver", classLoaderProvider, createDependencyResolutionServices(), instantiator, new AndroidPluginMapper(), new JCenterRepositoryConfigurer()));
        pluginHandler.getResolvers().add(new ModuleMappingPluginResolver("tomcat plugin resolver", classLoaderProvider, createDependencyResolutionServices(), instantiator, new TomcatPluginMapper(), new JCenterRepositoryConfigurer()));
    }

    private DependencyResolutionServices createDependencyResolutionServices() {
        return dependencyManagementServices.create(fileResolver, dependencyMetaDataProvider, projectFinder, new BasicDomainObjectContext());
    }

    private static class BasicDomainObjectContext implements DomainObjectContext {
        public String absoluteProjectPath(String name) {
            return name;
        }
    }

    private static class JCenterRepositoryConfigurer implements Action<RepositoryHandler> {
        public void execute(RepositoryHandler repositories) {
            repositories.jcenter();
        }
    }
}
