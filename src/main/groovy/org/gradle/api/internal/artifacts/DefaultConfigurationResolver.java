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
package org.gradle.api.internal.artifacts;

import groovy.lang.Closure;
import org.apache.ivy.core.report.ResolveReport;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.*;
import org.gradle.api.artifacts.specs.ConfigurationSpec;
import static org.gradle.api.artifacts.specs.DependencySpecs.*;
import org.gradle.api.artifacts.specs.Type;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.specs.AndSpec;
import static org.gradle.api.specs.Specs.and;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.util.ConfigureUtil;

import java.io.File;
import java.util.*;

/**
 * @author Hans Dockter
 */
public class DefaultConfigurationResolver extends AbstractFileCollection implements ConfigurationResolver {
    private Configuration configuration;
    private DependencyContainerInternal dependencyContainer;
    private ArtifactContainer artifactContainer;
    private ConfigurationContainer publishConfigurationContainer;
    private IvyService ivyService;
    private ResolverContainer dependencyResolvers;
    private File gradleUserHome;

    public DefaultConfigurationResolver(Configuration configuration, DependencyContainerInternal dependencyContainer,
                                        ArtifactContainer artifactContainer, ConfigurationContainer publishConfigurationContainer,
                                        ResolverContainer dependencyResolvers, IvyService ivyService, File gradleUserHome) {
        this.configuration = configuration;
        this.dependencyContainer = dependencyContainer;
        this.artifactContainer = artifactContainer;
        this.publishConfigurationContainer = publishConfigurationContainer;
        this.dependencyResolvers = dependencyResolvers;
        this.ivyService = ivyService;
        this.gradleUserHome = gradleUserHome;
    }

    public List<File> resolve() {
        return resolve(ResolveInstructionModifiers.DO_NOTHING_MODIFIER);
    }

    public List<File> resolve(ResolveInstructionModifier resolveInstructionModifier) {
        ResolveInstruction resolveInstruction = resolveInstructionModifier.modify(getResolveInstruction());
        return ivyService.resolve(getName(), dependencyContainer.getConfigurations(), dependencyContainer, dependencyResolvers.getResolverList(), resolveInstruction, gradleUserHome);
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
        return ivyService.resolveAsReport(getName(), dependencyContainer.getConfigurations(), dependencyContainer, dependencyResolvers.getResolverList(), resolveInstruction, gradleUserHome);
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
        ivyService.publish(getName(), publishInstruction, publishResolvers.getResolverList(), publishConfigurationContainer,
                dependencyContainer, artifactContainer, gradleUserHome);
    }

    public String getDisplayName() {
        return String.format("configuration '%s'", getName());
    }

    public Set<File> getFiles() {
        return new LinkedHashSet<File>(resolve());
    }

    public TaskDependency getBuildProjectDependencies() {
        return new TaskDependency() {
            public Set<? extends Task> getDependencies(Task task) {
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
                return taskDependency.getDependencies(task);
            }
        };
    }

    public TaskDependency getBuildArtifactDependencies() {
        return new TaskDependency() {
            public Set<? extends Task> getDependencies(Task task) {
                DefaultTaskDependency taskDependency = new DefaultTaskDependency();
                for (ConfigurationResolver configurationResolver : getExtendsFrom()) {
                    taskDependency.add(configurationResolver.getBuildArtifactDependencies());
                }
                for (PublishArtifact publishArtifact : getArtifacts()) {
                    taskDependency.add(publishArtifact.getTaskDependency());
                }
                return taskDependency.getDependencies(task);
            }
        };
    }

    public List<Dependency> getDependencies() {
        return dependencyContainer.getDependencies(confsWithoutExtensions(getName()));
    }

    public List<Dependency> getAllDependencies() {
        return dependencyContainer.getDependencies(confs(getName()));
    }

    public List<ProjectDependency> getProjectDependencies() {
        return dependencyContainer.getDependencies(andWithProjectSpec(confsWithoutExtensions(getName())));
    }

    public List<ProjectDependency> getAllProjectDependencies() {
        return dependencyContainer.getDependencies(andWithProjectSpec(confs(getName())));
    }

    public Set<PublishArtifact> getArtifacts() {
        return artifactContainer.getArtifacts(confsWithoutExtensions(getName()));
    }

    public Set<PublishArtifact> getAllArtifacts() {
        return artifactContainer.getArtifacts(confs(getName()));
    }

    private AndSpec andWithProjectSpec(ConfigurationSpec specs) {
        return and(specs, type(Type.PROJECT));
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
                    publishConfigurationContainer, dependencyResolvers, ivyService, gradleUserHome));
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

    public IvyService getIvyHandler() {
        return ivyService;
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

