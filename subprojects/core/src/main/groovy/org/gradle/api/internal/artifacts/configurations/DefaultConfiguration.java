/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.artifacts.configurations;

import groovy.lang.Closure;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.*;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.DefaultDomainObjectContainer;
import org.gradle.api.internal.artifacts.DefaultExcludeRule;
import org.gradle.api.internal.artifacts.IvyService;
import org.gradle.api.internal.file.AbstractFileCollection;
import org.gradle.api.internal.tasks.AbstractTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyInternal;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.util.WrapUtil;

import java.io.File;
import java.util.*;

import static org.apache.ivy.core.module.descriptor.Configuration.Visibility;

public class DefaultConfiguration extends AbstractFileCollection implements Configuration {
    private final String path;
    private final String name;

    private Visibility visibility = Visibility.PUBLIC;
    private boolean transitive = true;
    private Set<Configuration> extendsFrom = new LinkedHashSet<Configuration>();
    private String description;
    private ConfigurationsProvider configurationsProvider;

    private IvyService ivyService;

    private DefaultDomainObjectContainer<Dependency> dependencies =
            new DefaultDomainObjectContainer<Dependency>(Dependency.class);

    private Set<PublishArtifact> artifacts = new LinkedHashSet<PublishArtifact>();

    private Set<ExcludeRule> excludeRules = new LinkedHashSet<ExcludeRule>();

    private final ConfigurationTaskDependency taskDependency = new ConfigurationTaskDependency();

    // This lock only protects the following fields
    private final Object lock = new Object();
    private State state = State.UNRESOLVED;
    private ResolvedConfiguration cachedResolvedConfiguration;

    public DefaultConfiguration(String path, String name, ConfigurationsProvider configurationsProvider,
                                IvyService ivyService) {
        this.path = path;
        this.name = name;
        this.configurationsProvider = configurationsProvider;
        this.ivyService = ivyService;
    }

    public String getName() {
        return name;
    }

    public State getState() {
        synchronized (lock) {
            return state;
        }
    }

    public boolean isVisible() {
        return visibility == Visibility.PUBLIC;
    }

    public Configuration setVisible(boolean visible) {
        throwExceptionIfNotInUnresolvedState();
        this.visibility = visible ? Visibility.PUBLIC : Visibility.PRIVATE;
        return this;
    }

    public Set<Configuration> getExtendsFrom() {
        return Collections.unmodifiableSet(extendsFrom);
    }

    public Configuration setExtendsFrom(Set<Configuration> extendsFrom) {
        throwExceptionIfNotInUnresolvedState();
        this.extendsFrom = new HashSet<Configuration>();
        for (Configuration configuration : extendsFrom) {
            extendsFrom(configuration);
        }
        return this;
    }

    public Configuration extendsFrom(Configuration... extendsFrom) {
        throwExceptionIfNotInUnresolvedState();
        for (Configuration configuration : extendsFrom) {
            if (configuration.getHierarchy().contains(this)) {
                throw new InvalidUserDataException(String.format(
                        "Cyclic extendsFrom from %s and %s is not allowed. See existing hierarchy: %s", this,
                        configuration, configuration.getHierarchy()));
            }
            this.extendsFrom.add(configuration);
        }
        return this;
    }

    public boolean isTransitive() {
        return transitive;
    }

    public Configuration setTransitive(boolean transitive) {
        throwExceptionIfNotInUnresolvedState();
        this.transitive = transitive;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public Configuration setDescription(String description) {
        throwExceptionIfNotInUnresolvedState();
        this.description = description;
        return this;
    }

    public Set<Configuration> getHierarchy() {
        Set<Configuration> result = WrapUtil.<Configuration>toLinkedSet(this);
        collectSuperConfigs(this, result);
        return result;
    }

    private void collectSuperConfigs(Configuration configuration, Set<Configuration> result) {
        for (Configuration superConfig : configuration.getExtendsFrom()) {
            if (result.contains(superConfig)) {
                result.remove(superConfig);
            }
            result.add(superConfig);
            collectSuperConfigs(superConfig, result);
        }
    }

    public Set<Configuration> getAll() {
        return configurationsProvider.getAll();
    }

    public Set<File> resolve() {
        return getFiles();
    }

    public Set<File> getFiles() {
        return fileCollection(Specs.SATISFIES_ALL).getFiles();
    }

    public Set<File> files(Dependency... dependencies) {
        return fileCollection(dependencies).getFiles();
    }

    public Set<File> files(Closure dependencySpecClosure) {
        return fileCollection(dependencySpecClosure).getFiles();
    }

    public Set<File> files(Spec<Dependency> dependencySpec) {
        return fileCollection(dependencySpec).getFiles();
    }

    public FileCollection fileCollection(Spec<Dependency> dependencySpec) {
        return new ConfigurationFileCollection(dependencySpec);
    }

    public FileCollection fileCollection(Closure dependencySpecClosure) {
        return new ConfigurationFileCollection(dependencySpecClosure);
    }

    public FileCollection fileCollection(Dependency... dependencies) {
        return new ConfigurationFileCollection(WrapUtil.toLinkedSet(dependencies));
    }

    public ResolvedConfiguration getResolvedConfiguration() {
        synchronized (lock) {
            if (state == State.UNRESOLVED) {
                cachedResolvedConfiguration = ivyService.resolve(this);
                if (cachedResolvedConfiguration.hasError()) {
                    state = State.RESOLVED_WITH_FAILURES;
                } else {
                    state = State.RESOLVED;
                }
            }
            return cachedResolvedConfiguration;
        }
    }

    public void publish(List<DependencyResolver> publishResolvers, File descriptorDestination) {
        ivyService.publish(getHierarchy(), descriptorDestination, publishResolvers);
    }

    public TaskDependency getBuildDependencies() {
        return taskDependency;
    }

    /**
     * {@inheritDoc}
     */
    public TaskDependency getTaskDependencyFromProjectDependency(final boolean useDependedOn, final String taskName) {
        return new AbstractTaskDependency() {
            public void resolve(TaskDependencyResolveContext context) {
                if (useDependedOn) {
                    addTaskDependenciesFromProjectsIDependOn(taskName, context);
                } else {
                    Project thisProject = context.getTask().getProject();
                    addTaskDependenciesFromProjectsDependingOnMe(thisProject, taskName, context);
                }
            }

            private void addTaskDependenciesFromProjectsIDependOn(final String taskName,
                                                                  final TaskDependencyResolveContext context) {
                Set<ProjectDependency> projectDependencies = getAllDependencies(ProjectDependency.class);
                for (ProjectDependency projectDependency : projectDependencies) {
                    Task nextTask = projectDependency.getDependencyProject().getTasks().findByName(taskName);
                    if (nextTask != null) {
                        context.add(nextTask);
                    }
                }
            }

            private void addTaskDependenciesFromProjectsDependingOnMe(final Project thisProject, final String taskName,
                                                                      final TaskDependencyResolveContext context) {
                Set<Task> tasksWithName = thisProject.getRootProject().getTasksByName(taskName, true);
                for (Task nextTask : tasksWithName) {
                    Configuration configuration = nextTask.getProject().getConfigurations().findByName(getName());
                    if (configuration != null && doesConfigurationDependOnProject(configuration, thisProject)) {
                        context.add(nextTask);
                    }
                }
            }
        };
    }

    private static boolean doesConfigurationDependOnProject(Configuration configuration, Project project) {
        Set<ProjectDependency> projectDependencies = configuration.getAllDependencies(ProjectDependency.class);
        for (ProjectDependency projectDependency : projectDependencies) {
            if (projectDependency.getDependencyProject().equals(project)) {
                return true;
            }
        }
        return false;
    }

    public TaskDependency getBuildArtifacts() {
        return getAllArtifactFiles().getBuildDependencies();
    }

    public Set<Dependency> getDependencies() {
        return dependencies.getAll();
    }

    public Set<Dependency> getAllDependencies() {
        return Configurations.getDependencies(getHierarchy(), Specs.<Dependency>satisfyAll());
    }

    public <T extends Dependency> Set<T> getDependencies(Class<T> type) {
        return filter(type, getDependencies());
    }

    private <T extends Dependency> Set<T> filter(Class<T> type, Set<Dependency> dependencySet) {
        Set<T> matches = new LinkedHashSet<T>();
        for (Dependency dependency : dependencySet) {
            if (type.isInstance(dependency)) {
                matches.add(type.cast(dependency));
            }
        }
        return matches;
    }

    public <T extends Dependency> Set<T> getAllDependencies(Class<T> type) {
        return filter(type, getAllDependencies());
    }

    public void addDependency(Dependency dependency) {
        throwExceptionIfNotInUnresolvedState();
        dependencies.addObject(dependency);
    }

    public Configuration addArtifact(PublishArtifact artifact) {
        throwExceptionIfNotInUnresolvedState();
        artifacts.add(artifact);
        return this;
    }

    public Configuration removeArtifact(PublishArtifact artifact) {
        throwExceptionIfNotInUnresolvedState();
        artifacts.remove(artifact);
        return this;
    }

    public Set<PublishArtifact> getArtifacts() {
        return Collections.unmodifiableSet(artifacts);
    }

    public Set<PublishArtifact> getAllArtifacts() {
        return Configurations.getArtifacts(this.getHierarchy(), Specs.SATISFIES_ALL);
    }

    public FileCollection getAllArtifactFiles() {
        return new ArtifactsFileCollection();
    }

    public Set<ExcludeRule> getExcludeRules() {
        return Collections.unmodifiableSet(excludeRules);
    }

    public void setExcludeRules(Set<ExcludeRule> excludeRules) {
        throwExceptionIfNotInUnresolvedState();
        this.excludeRules = excludeRules;
    }

    public DefaultConfiguration exclude(Map<String, String> excludeRuleArgs) {
        throwExceptionIfNotInUnresolvedState();
        excludeRules.add(new DefaultExcludeRule(excludeRuleArgs));
        return this;
    }

    public String getUploadTaskName() {
        return Configurations.uploadTaskName(getName());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultConfiguration that = (DefaultConfiguration) o;
        return path.equals(that.path);
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    public String getDisplayName() {
        return String.format("configuration '%s'", path);
    }

    public Configuration getConfiguration(Dependency dependency) {
        for (Configuration configuration : getHierarchy()) {
            if (configuration.getDependencies().contains(dependency)) {
                return configuration;
            }
        }
        return null;
    }

    public Configuration copy() {
        return createCopy(getDependencies());
    }

    public Configuration copyRecursive() {
        return createCopy(getAllDependencies());
    }

    public Configuration copy(Spec<Dependency> dependencySpec) {
        return createCopy(Specs.filterIterable(getDependencies(), dependencySpec));
    }

    public Configuration copyRecursive(Spec<Dependency> dependencySpec) {
        return createCopy(Specs.filterIterable(getAllDependencies(), dependencySpec));
    }

    private DefaultConfiguration createCopy(Set<Dependency> dependencies) {
        DetachedConfigurationsProvider configurationsProvider = new DetachedConfigurationsProvider();
        DefaultConfiguration copiedConfiguration = new DefaultConfiguration(path + "Copy", name + "Copy",
                configurationsProvider, ivyService);
        configurationsProvider.setTheOnlyConfiguration(copiedConfiguration);
        // state, cachedResolvedConfiguration, and extendsFrom intentionally not copied - must re-resolve copy
        // copying extendsFrom could mess up dependencies when copy was re-resolved

        copiedConfiguration.visibility = visibility;
        copiedConfiguration.transitive = transitive;
        copiedConfiguration.description = description;

        for (PublishArtifact artifact : getAllArtifacts()) {
            copiedConfiguration.addArtifact(artifact);
        }

        // todo An ExcludeRule is a value object but we don't enforce immutability for DefaultExcludeRule as strong as we
        // should (we expose the Map). We should provide a better API for ExcludeRule (I don't want to use unmodifiable Map).
        // As soon as DefaultExcludeRule is truly immutable, we don't need to create a new instance of DefaultExcludeRule. 
        for (ExcludeRule excludeRule : getExcludeRules()) {
            copiedConfiguration.excludeRules.add(new DefaultExcludeRule(excludeRule.getExcludeArgs()));
        }

        for (Dependency dependency : dependencies) {
            copiedConfiguration.addDependency(dependency.copy());
        }
        return copiedConfiguration;
    }

    public Configuration copy(Closure dependencySpec) {
        return copy(Specs.<Dependency>convertClosureToSpec(dependencySpec));
    }

    public Configuration copyRecursive(Closure dependencySpec) {
        return copyRecursive(Specs.<Dependency>convertClosureToSpec(dependencySpec));
    }

    private void throwExceptionIfNotInUnresolvedState() {
        if (getState() != State.UNRESOLVED) {
            throw new InvalidUserDataException("You can't change a configuration which is not in unresolved state!");
        }
    }

    class ArtifactsFileCollection extends AbstractFileCollection {
        private final TaskDependencyInternal taskDependency = new AbstractTaskDependency() {
            public void resolve(TaskDependencyResolveContext context) {
                for (Configuration configuration : getExtendsFrom()) {
                    context.add(configuration.getBuildArtifacts());
                }
                for (PublishArtifact publishArtifact : getArtifacts()) {
                    context.add(publishArtifact);
                }
            }
        };

        public String getDisplayName() {
            return String.format("%s artifacts", DefaultConfiguration.this);
        }

        @Override
        public TaskDependency getBuildDependencies() {
            return taskDependency;
        }

        public Set<File> getFiles() {
            Set<File> files = new LinkedHashSet<File>();
            for (PublishArtifact artifact : getAllArtifacts()) {
                files.add(artifact.getFile());
            }
            return files;
        }
    }

    class ConfigurationFileCollection extends AbstractFileCollection {
        private Spec<Dependency> dependencySpec;

        private ConfigurationFileCollection(Spec<Dependency> dependencySpec) {
            this.dependencySpec = dependencySpec;
        }

        public ConfigurationFileCollection(Closure dependencySpecClosure) {
            this.dependencySpec = Specs.convertClosureToSpec(dependencySpecClosure);
        }

        public ConfigurationFileCollection(final Set<Dependency> dependencies) {
            this.dependencySpec = new Spec<Dependency>() {
                public boolean isSatisfiedBy(Dependency element) {
                    return dependencies.contains(element);
                }
            };
        }

        public Spec<Dependency> getDependencySpec() {
            return dependencySpec;
        }

        public String getDisplayName() {
            return String.format("%s dependencies", DefaultConfiguration.this);
        }

        public Set<File> getFiles() {
            synchronized (lock) {
                ResolvedConfiguration resolvedConfiguration = getResolvedConfiguration();
                if (getState() == State.RESOLVED_WITH_FAILURES) {
                    resolvedConfiguration.rethrowFailure();
                }
                return resolvedConfiguration.getFiles(dependencySpec);
            }
        }
    }

    public Action<? super Dependency> whenDependencyAdded(Action<? super Dependency> action) {
        return dependencies.whenObjectAdded(action);
    }

    public void whenDependencyAdded(Closure closure) {
        dependencies.whenObjectAdded(closure);
    }

    public void allDependencies(Action<? super Dependency> action) {
        dependencies.all(action);
    }

    public void allDependencies(Closure action) {
        dependencies.all(action);
    }

    private class ConfigurationTaskDependency extends AbstractTaskDependency {
        @Override
        public String toString() {
            return String.format("build dependencies %s", DefaultConfiguration.this);
        }

        public void resolve(TaskDependencyResolveContext context) {
            for (Configuration configuration : getExtendsFrom()) {
                context.add(configuration);
            }
            for (SelfResolvingDependency dependency : DefaultConfiguration.this.getDependencies(
                    SelfResolvingDependency.class)) {
                context.add(dependency);
            }
        }
    }
}

