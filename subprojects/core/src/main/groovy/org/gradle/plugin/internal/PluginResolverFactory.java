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
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.plugin.resolve.internal.*;

import java.util.LinkedList;
import java.util.List;

public class PluginResolverFactory {

    private final PluginRegistry pluginRegistry;
    private final Instantiator instantiator;
    private final DependencyManagementServices dependencyManagementServices;
    private final FileResolver fileResolver;
    private final DependencyMetaDataProvider dependencyMetaDataProvider;

    private final ProjectFinder projectFinder = new ProjectFinder() {
        public ProjectInternal getProject(String path) {
            throw new UnknownProjectException("Cannot use project dependencies in a plugin resolution definition.");
        }
    };

    public PluginResolverFactory(PluginRegistry pluginRegistry, Instantiator instantiator, DependencyManagementServices dependencyManagementServices, FileResolver fileResolver, DependencyMetaDataProvider dependencyMetaDataProvider) {
        this.pluginRegistry = pluginRegistry;
        this.instantiator = instantiator;
        this.dependencyManagementServices = dependencyManagementServices;
        this.fileResolver = fileResolver;
        this.dependencyMetaDataProvider = dependencyMetaDataProvider;
    }

    public PluginResolver createPluginResolver() {
        List<PluginResolver> resolvers = new LinkedList<PluginResolver>();
        addDefaultResolvers(resolvers);
        return new CompositePluginResolver(resolvers);
    }

    private void addDefaultResolvers(List<PluginResolver> resolvers) {
        resolvers.add(new PluginRegistryPluginResolver(pluginRegistry));
        resolvers.add(new ModuleMappingPluginResolver("android plugin resolver", createDependencyResolutionServices(), instantiator, new AndroidPluginMapper(), new JCenterRepositoryConfigurer()));
        resolvers.add(new ModuleMappingPluginResolver("jcenter plugin resolver", createDependencyResolutionServices(), instantiator, new JCenterPluginMapper(), new JCenterRepositoryConfigurer()));
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
