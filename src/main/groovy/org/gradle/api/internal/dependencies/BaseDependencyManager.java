/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.internal.dependencies;

import groovy.lang.Closure;
import org.apache.ivy.Ivy;
import org.apache.ivy.core.module.descriptor.*;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.resolver.*;
import org.gradle.api.DependencyManager;
import org.gradle.api.Transformer;
import org.gradle.api.Project;
import org.gradle.api.filter.FilterSpec;
import org.gradle.api.dependencies.maven.Conf2ScopeMappingContainer;
import org.gradle.api.internal.dependencies.maven.dependencies.DefaultConf2ScopeMappingContainer;
import org.gradle.api.internal.dependencies.ivy.IvyHandler;
import org.gradle.api.internal.dependencies.ivy.ResolverFactory;
import org.gradle.api.internal.dependencies.ivy.BuildResolverHandler;
import org.gradle.api.dependencies.*;
import org.gradle.api.dependencies.Configuration;
import org.gradle.util.WrapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

/**
 * @author Hans Dockter
 */
public class BaseDependencyManager implements DependencyManagerInternal {
    private static Logger logger = LoggerFactory.getLogger(DefaultDependencyManager.class);

    private Project project;

    private BuildResolverHandler buildResolverHandler;

    private ResolverContainer classpathResolvers;

    private Conf2ScopeMappingContainer defaultConf2ScopeMapping = new DefaultConf2ScopeMappingContainer();

    private DependencyContainerInternal dependencyContainer;

    private ConfigurationContainer configurationContainer;

    private ArtifactContainer artifactContainer;

    private IvyHandler ivyHandler;

    private ResolverFactory resolverFactory;

    private ConfigurationResolverFactory configurationResolverFactory;

    public BaseDependencyManager() {

    }

    public BaseDependencyManager(Project project, DependencyContainerInternal dependencyContainer, ArtifactContainer artifactContainer,
                                 ConfigurationContainer configurationContainer, ConfigurationResolverFactory configurationResolverFactory,
                                 ResolverContainer classpathResolvers, ResolverFactory resolverFactory, BuildResolverHandler buildResolverHandler,
                                 IvyHandler ivyHandler) {
        this.project = project;
        this.dependencyContainer = dependencyContainer;
        this.artifactContainer = artifactContainer;
        this.configurationContainer = configurationContainer;
        this.configurationResolverFactory = configurationResolverFactory;
        this.classpathResolvers = classpathResolvers;
        this.resolverFactory = resolverFactory;
        this.buildResolverHandler = buildResolverHandler;
        this.ivyHandler = ivyHandler;
    }

    public Project getProject() {
        return project;
    }

    public List<? extends Dependency> getDependencies() {
        return dependencyContainer.getDependencies();
    }

    public <T extends Dependency> List<T> getDependencies(FilterSpec<T> filter) {
        return dependencyContainer.getDependencies(filter);
    }

    public void addDependencies(Dependency... dependencies) {
        dependencyContainer.addDependencies(dependencies);
    }

    public void dependencies(List<String> confs, Object... dependencies) {
        dependencyContainer.dependencies(confs, dependencies);
    }

    public void dependencies(Map<Configuration, List<String>> configurationMappings, Object... dependencies) {
        dependencyContainer.dependencies(configurationMappings, dependencies);
    }

    public Dependency dependency(List<String> confs, Object userDependencyDescription, Closure configureClosure) {
        return dependencyContainer.dependency(confs, userDependencyDescription, configureClosure);
    }

    public Dependency dependency(Map<Configuration, List<String>> configurationMappings, Object userDependencyDescription, Closure configureClosure) {
        return dependencyContainer.dependency(configurationMappings, userDependencyDescription, configureClosure);
    }

    public ClientModule clientModule(List<String> confs, String moduleDescriptor) {
        return dependencyContainer.clientModule(confs, moduleDescriptor);
    }

    public ClientModule clientModule(Map<Configuration, List<String>> configurationMappings, String moduleDescriptor) {
        return dependencyContainer.clientModule(configurationMappings, moduleDescriptor);
    }

    public ClientModule clientModule(List<String> confs, String moduleDescriptor, Closure configureClosure) {
        return dependencyContainer.clientModule(confs, moduleDescriptor, configureClosure);
    }

    public ClientModule clientModule(Map<Configuration, List<String>> configurationMappings, String moduleDescriptor, Closure configureClosure) {
        return dependencyContainer.clientModule(configurationMappings, moduleDescriptor, configureClosure);
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public ConfigurationResolver addConfiguration(String configuration) {
        return addConfiguration(configuration, null);
    }

    public ConfigurationResolver addConfiguration(String name, Closure configureClosure) {
        return createConfigurationResolver(configurationContainer.add(name, configureClosure));
    }

    public ConfigurationResolver findConfiguration(String name) {
        return createConfigurationResolver(configurationContainer.find(name));
    }

    public ConfigurationResolver configuration(String name) throws UnknownConfigurationException {
        return configuration(name, null);
    }

    public ConfigurationResolver configuration(String name, Closure configureClosure) throws UnknownConfigurationException {
        return createConfigurationResolver(configurationContainer.get(name, configureClosure));
    }

    public List<ConfigurationResolver> getConfigurations() {
        List<ConfigurationResolver> configurations = new ArrayList<ConfigurationResolver>();
        for (Configuration configuration : configurationContainer.get()) {
            configurations.add(createConfigurationResolver(configuration));
        }
        return configurations;
    }

    private ConfigurationResolver createConfigurationResolver(Configuration configuration) {
        if (configuration == null) {
            return null;
        }
        return configurationResolverFactory.createConfigurationResolver(configuration, dependencyContainer, classpathResolvers,
                artifactContainer, configurationContainer);
    }

    public ConfigurationContainer getConfigurationContainer() {
        return configurationContainer;
    }

    public void setConfigurationContainer(ConfigurationContainer configurationContainer) {
        this.configurationContainer = configurationContainer;
    }

    public Ivy getIvy() {
        return ivy(new ArrayList<DependencyResolver>());
    }

    public Ivy ivy(List<DependencyResolver> resolvers) {
        return ivyHandler.ivy(classpathResolvers.getResolverList(),
                resolvers, project.getBuild().getGradleUserHomeDir(), dependencyContainer.getClientModuleRegistry());
    }

    public RepositoryResolver getBuildResolver() {
        return buildResolverHandler.getBuildResolver();
    }

    public File getBuildResolverDir() {
        return buildResolverHandler.getBuildResolverDir();
    }

    public FileSystemResolver addFlatDirResolver(String name, Object... dirs) {
        FileSystemResolver resolver = classpathResolvers.createFlatDirResolver(name, dirs);
        return (FileSystemResolver) classpathResolvers.add(resolver);
    }

    public DependencyResolver addMavenRepo(String... jarRepoUrls) {
        return classpathResolvers.add(classpathResolvers.createMavenRepoResolver(DependencyManager.DEFAULT_MAVEN_REPO_NAME,
                DependencyManager.MAVEN_REPO_URL, jarRepoUrls));
    }

    public DependencyResolver addMavenStyleRepo(String name, String root, String... jarRepoUrls) {
        return classpathResolvers.add(classpathResolvers.createMavenRepoResolver(name, root, jarRepoUrls));
    }

    public ResolverFactory getResolverFactory() {
        return resolverFactory;
    }

    public IvyHandler getIvyConverter() {
        return ivyHandler;
    }

    public void setIvyConverter(IvyHandler ivyHandler) {
        this.ivyHandler = ivyHandler;
    }

    public BuildResolverHandler getBuildResolverHandler() {
        return buildResolverHandler;
    }

    public void setBuildResolverHandler(BuildResolverHandler buildResolverHandler) {
        this.buildResolverHandler = buildResolverHandler;
    }

    public ResolverContainer getClasspathResolvers() {
        return classpathResolvers;
    }

    public void setClasspathResolvers(ResolverContainer classpathResolvers) {
        this.classpathResolvers = classpathResolvers;
    }

    public ExcludeRuleContainer getExcludeRules() {
        return dependencyContainer.getExcludeRules();
    }

    public Conf2ScopeMappingContainer getDefaultMavenScopeMapping() {
        return defaultConf2ScopeMapping;
    }

    public void setDefaultConf2ScopeMapping(Conf2ScopeMappingContainer defaultConf2ScopeMapping) {
        this.defaultConf2ScopeMapping = defaultConf2ScopeMapping;
    }

    public void addIvySettingsTransformer(Transformer<IvySettings> transformer) {
        ivyHandler.getSettingsConverter().addIvyTransformer(transformer);
    }

    public void addIvySettingsTransformer(Closure transformer) {
        ivyHandler.getSettingsConverter().addIvyTransformer(transformer);
    }

    public void addIvyModuleTransformer(Transformer<DefaultModuleDescriptor> transformer) {
        ivyHandler.getModuleDescriptorConverter().addIvyTransformer(transformer);
    }

    public void addIvyModuleTransformer(Closure transformer) {
        ivyHandler.getModuleDescriptorConverter().addIvyTransformer(transformer);
    }

    public DependencyContainer getDependencyContainer() {
        return dependencyContainer;
    }

    public void setDependencyContainer(DependencyContainerInternal dependencyContainer) {
        this.dependencyContainer = dependencyContainer;
    }

    public IvyHandler getIvyHandler() {
        return ivyHandler;
    }

    public ModuleDescriptor createModuleDescriptor(FilterSpec<Configuration> configurationFilter, FilterSpec<Dependency> dependencyFilter,
                                                   FilterSpec<PublishArtifact> artifactFilter) {
        return ivyHandler.getModuleDescriptorConverter().convert(new HashMap<String, Boolean>(), configurationContainer, configurationFilter,
                dependencyContainer, dependencyFilter, artifactContainer, artifactFilter);
    }

    public ArtifactContainer getArtifactContainer() {
        return artifactContainer;
    }

    public ConfigurationResolverFactory getConfigurationResolverFactory() {
        return configurationResolverFactory;
    }

    public ResolverContainer createResolverContainer() {
        ResolverContainer resolverContainer = new ResolverContainer(resolverFactory);
        resolverContainer.setDependencyManager(this);
        return resolverContainer;
    }

    public Set<PublishArtifact> getArtifacts() {
        return artifactContainer.getArtifacts();
    }

    public void addArtifacts(PublishArtifact... artifacts) {
        artifactContainer.addArtifacts(artifacts);
    }
}