/*
 * Copyright 2007-2009 the original author or authors.
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

import org.gradle.api.dependencies.*;
import org.gradle.api.dependencies.Configuration;
import org.gradle.api.dependencies.filter.ConfSpec;
import org.gradle.api.dependencies.filter.TypeSpec;
import org.gradle.api.dependencies.filter.Type;
import org.gradle.api.dependencies.filter.DependencyFilters;
import static org.gradle.api.dependencies.filter.DependencyFilters.*;
import org.gradle.api.internal.dependencies.ivy.IvyHandler;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.Transformer;
import org.gradle.api.filter.AndSpec;
import org.gradle.api.filter.Filters;
import static org.gradle.api.filter.Filters.*;
import org.gradle.util.ConfigureUtil;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.module.descriptor.*;

import java.io.File;
import java.util.*;

import groovy.lang.Closure;

/**
 * @author Hans Dockter
 */
public class DefaultConfigurationResolver implements ConfigurationResolver {
    private Configuration configuration;
    private DependencyContainerInternal dependencyContainer;
    private ArtifactContainer artifactContainer;
    private ConfigurationContainer publishConfigurationContainer;
    private IvyHandler ivyHandler;
    private ResolverContainer dependencyResolvers;
    private File gradleUserHome;

    public DefaultConfigurationResolver(Configuration configuration, DependencyContainerInternal dependencyContainer,
                                        ArtifactContainer artifactContainer, ConfigurationContainer publishConfigurationContainer,
                                        ResolverContainer dependencyResolvers, IvyHandler ivyHandler, File gradleUserHome) {
        this.configuration = configuration;
        this.dependencyContainer = dependencyContainer;
        this.artifactContainer = artifactContainer;
        this.publishConfigurationContainer = publishConfigurationContainer;
        this.dependencyResolvers = dependencyResolvers;
        this.ivyHandler = ivyHandler;
        this.gradleUserHome = gradleUserHome;
    }

    public List<File> resolve() {
        return resolve(ResolveInstructionModifiers.DO_NOTHING_MODIFIER);
    }

    public List<File> resolve(ResolveInstructionModifier resolveInstructionModifier) {
        ResolveInstruction resolveInstruction = resolveInstructionModifier.modify(getResolveInstruction());
        return ivyHandler.resolve(getName(), dependencyContainer.getConfigurations(), dependencyContainer, dependencyResolvers.getResolverList(), resolveInstruction, gradleUserHome);
    }

    public List<File> resolve(final Closure resolveInstructionConfigureClosure) {
        return resolve(new ResolveInstructionModifier() {
            public ResolveInstruction modify(ResolveInstruction resolveInstruction) {
                ResolveInstruction newResolveInstruction = new ResolveInstruction(resolveInstruction);
                ConfigureUtil.configure(resolveInstructionConfigureClosure, newResolveInstruction);
                return newResolveInstruction;
            }
        });
    }

    public ResolveReport resolveAsReport(ResolveInstructionModifier resolveInstructionModifier) {
        ResolveInstruction resolveInstruction = resolveInstructionModifier.modify(getResolveInstruction());
        return ivyHandler.resolveAsReport(getName(), dependencyContainer.getConfigurations(), dependencyContainer, dependencyResolvers.getResolverList(), resolveInstruction, gradleUserHome);
    }

    public ResolveReport resolveAsReport(final Closure resolveInstructionConfigureClosure) {
        return resolveAsReport(new ResolveInstructionModifier() {
            public ResolveInstruction modify(ResolveInstruction resolveInstruction) {
                ResolveInstruction newResolveInstruction = new ResolveInstruction(resolveInstruction);
                ConfigureUtil.configure(resolveInstructionConfigureClosure, newResolveInstruction);
                return newResolveInstruction;
            }
        });
    }

    public void publish(ResolverContainer publishResolvers, PublishInstruction publishInstruction) {
        ivyHandler.publish(getName(), publishInstruction, publishResolvers.getResolverList(), publishConfigurationContainer,
                dependencyContainer, artifactContainer, gradleUserHome);
    }

    public File getSingleFile() throws IllegalStateException {
        List<File> files = resolve();
        if (files.size() != 1) {
            throw new IllegalStateException(String.format("Configuration '%s' does not resolve to a single file.",
                    getName()));
        }
        return files.get(0);
    }

    public Set<File> getFiles() {
        return new HashSet(resolve());
    }

    public Iterator<File> iterator() {
        return resolve().iterator();
    }

    public TaskDependency getBuildProjectDependencies() {
        DefaultTaskDependency taskDependency = new DefaultTaskDependency();
        for (ConfigurationResolver configurationResolver : getExtendsFrom()) {
            taskDependency.add(configurationResolver.getBuildProjectDependencies());
        }
        for (ProjectDependency projectDependency : getProjectDependencies()) {
            List<String> dependencyConfigurations = projectDependency.getDependencyConfigurations(getName());
            for (String dependencyConfiguration : dependencyConfigurations) {
                ConfigurationResolver configuration = projectDependency.getDependencyProject().getDependencies().configuration(dependencyConfiguration);
                taskDependency.add(projectDependency.getDependencyProject().task(configuration.getUploadInternalTaskName()));
            }
        }
        return taskDependency;
    }

    public List<Dependency> getDependencies() {
        return dependencyContainer.getDependencies(confsWithoutExtensions(getName()));
    }

    public List<ProjectDependency> getProjectDependencies() {
        return dependencyContainer.getDependencies(and(confsWithoutExtensions(getName()), type(Type.PROJECT)));
    }

    public String getName() {
        return configuration.getName();
    }

    public boolean isVisible() {
        return configuration.isVisible();
    }

    public Configuration setVisible(boolean visible) {
        configuration.setVisible(visible);
        return this;
    }

    public Set<ConfigurationResolver> getExtendsFrom() {
        return transformConfigurationSet(configuration.getExtendsFrom());
    }

    public Set<ConfigurationResolver> getChain() {
        return transformConfigurationSet(configuration.getChain());
    }

    public ResolveInstruction getResolveInstruction() {
        return configuration.getResolveInstruction();
    }

    private Set<ConfigurationResolver> transformConfigurationSet(Set<? extends Configuration> configurations) {
        Set<ConfigurationResolver> resultSet = new HashSet<ConfigurationResolver>();
        for (Configuration configuration : configurations) {
            resultSet.add(new DefaultConfigurationResolver(configuration, dependencyContainer, artifactContainer,
                    publishConfigurationContainer, dependencyResolvers, ivyHandler, gradleUserHome));
        }
        return resultSet;
        
    }

    public ConfigurationResolver setExtendsFrom(Set<String> superConfigs) {
        configuration.setExtendsFrom(superConfigs);
        return this;
    }

    public ConfigurationResolver extendsFrom(String... superConfigs) {
        configuration.extendsFrom(superConfigs);
        return this;
    }

    public boolean isTransitive() {
        return configuration.isTransitive();
    }

    public ConfigurationResolver setTransitive(boolean t) {
        configuration.setTransitive(t);
        return this;
    }

    public String getDescription() {
        return configuration.getDescription();
    }

    public ConfigurationResolver setDescription(String description) {
        configuration.setDescription(description);
        return this;
    }

    public String getUploadInternalTaskName() {
        return ConfigurationResolvers.uploadInternalTaskName(getName());
    }

    public String getUploadTaskName() {
        return ConfigurationResolvers.uploadTaskName(getName());
    }

    public org.apache.ivy.core.module.descriptor.Configuration getIvyConfiguration(boolean transitive) {
        return configuration.getIvyConfiguration(transitive);
    }

    public void addIvyTransformer(Transformer<org.apache.ivy.core.module.descriptor.Configuration> transformer) {
        configuration.addIvyTransformer(transformer);
    }

    public void addIvyTransformer(Closure transformer) {
        configuration.addIvyTransformer(transformer);
    }

    public Configuration getWrappedConfiguration() {
        return configuration;
    }

    public DependencyContainerInternal getDependencyContainer() {
        return dependencyContainer;
    }

    public ArtifactContainer getArtifactContainer() {
        return artifactContainer;
    }

    public ConfigurationContainer getPublishConfigurationContainer() {
        return publishConfigurationContainer;
    }

    public IvyHandler getIvyHandler() {
        return ivyHandler;
    }

    public File getGradleUserHome() {
        return gradleUserHome;
    }

    public ResolverContainer getDependencyResolvers() {
        return dependencyResolvers;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DefaultConfigurationResolver that = (DefaultConfigurationResolver) o;

        if (configuration != null ? !configuration.equals(that.configuration) : that.configuration != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = configuration != null ? configuration.hashCode() : 0;
        return result;
    }

    @Override
    public String toString() {
        return "DefaultConfigurationResolver{" +
                "configuration=" + configuration +
                '}';
    }
}

