package org.gradle.api.internal.artifacts.configurations;

import groovy.lang.Closure;
import static org.apache.ivy.core.module.descriptor.Configuration.Visibility;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Task;
import org.gradle.api.artifacts.*;
import org.gradle.api.internal.artifacts.AbstractFileCollection;
import org.gradle.api.internal.artifacts.DefaultExcludeRule;
import org.gradle.api.internal.artifacts.IvyService;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.util.WrapUtil;

import java.io.File;
import java.util.*;

public class DefaultConfiguration extends AbstractFileCollection implements Configuration {
    private final String name;

    private Visibility visibility = Visibility.PUBLIC;
    private boolean transitive = true;
    private Set<Configuration> extendsFrom = new LinkedHashSet<Configuration>();
    private String description;
    private ConfigurationsProvider configurationsProvider;

    private IvyService ivyService;

    private ProjectDependenciesBuildInstruction projectDependenciesBuildInstruction;

    private Set<Dependency> dependencies = new LinkedHashSet<Dependency>();

    private Set<PublishArtifact> artifacts = new LinkedHashSet<PublishArtifact>();

    private Set<ExcludeRule> excludeRules = new LinkedHashSet<ExcludeRule>();

    private State state = State.UNRESOLVED;

    private ResolvedConfiguration cachedResolvedConfiguration = null;

    public DefaultConfiguration(String name, ConfigurationsProvider configurationsProvider, IvyService ivyService,
                                ProjectDependenciesBuildInstruction projectDependenciesBuildInstruction) {
        this.name = name;
        this.configurationsProvider = configurationsProvider;
        this.ivyService = ivyService;
        this.projectDependenciesBuildInstruction = projectDependenciesBuildInstruction;
    }

    public String getName() {
        return name;
    }

    public State getState() {
        return state;
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
        return extendsFrom;
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
                throw new InvalidUserDataException(String.format("Cyclic extendsFrom from %s and %s is not allowed. See existing hierarchy: %s", this, configuration, configuration.getHierarchy()));
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

    public List<Configuration> getHierarchy() {
        List<Configuration> result = WrapUtil.<Configuration>toList(this);
        collectSuperConfigs(this, result);
        return result;
    }

    private void collectSuperConfigs(Configuration configuration, List<Configuration> result) {
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

    public void publish(List<DependencyResolver> publishResolvers, PublishInstruction publishInstruction) {
        ivyService.publish(new HashSet<Configuration>(getHierarchy()), publishInstruction, publishResolvers);
    }

    public TaskDependency getBuildDependencies() {
        if (!projectDependenciesBuildInstruction.isRebuild()) {
            return new DefaultTaskDependency();
        }
        return new TaskDependency() {
            public Set<? extends Task> getDependencies(Task task) {
                DefaultTaskDependency taskDependency = new DefaultTaskDependency();
                addBuildDependenciesFromExtendedConfigurations(taskDependency);
                addUploadTaskAndAdditionalTasksFromProjectDependencies(taskDependency);
                return taskDependency.getDependencies(task);
            }
        };
    }

    private void addUploadTaskAndAdditionalTasksFromProjectDependencies(DefaultTaskDependency taskDependency) {
        for (ProjectDependency projectDependency : getDependencies(ProjectDependency.class)) {
            Configuration configuration = projectDependency.getProjectConfiguration();
            for (String taskName : projectDependenciesBuildInstruction.getTaskNames()) {
                taskDependency.add(projectDependency.getDependencyProject().getTasks().getByName(taskName));
            }
            taskDependency.add(projectDependency.getDependencyProject().getTasks().getByName(configuration.getUploadInternalTaskName()));
        }
    }

    private void addBuildDependenciesFromExtendedConfigurations(DefaultTaskDependency taskDependency) {
        for (Configuration configuration : getExtendsFrom()) {
            taskDependency.add(configuration.getBuildDependencies());
        }
    }

    public TaskDependency getBuildArtifacts() {
        return new TaskDependency() {
            public Set<? extends Task> getDependencies(Task task) {
                DefaultTaskDependency taskDependency = new DefaultTaskDependency();
                addBuildArtifactsFromExtendedConfigurations(taskDependency);
                addTasksForBuildingArtifacts(taskDependency);
                return taskDependency.getDependencies(task);
            }
        };
    }

    private void addTasksForBuildingArtifacts(DefaultTaskDependency taskDependency) {
        for (PublishArtifact publishArtifact : getArtifacts()) {
            taskDependency.add(publishArtifact.getTaskDependency());
        }
    }

    private void addBuildArtifactsFromExtendedConfigurations(DefaultTaskDependency taskDependency) {
        for (Configuration configuration : getExtendsFrom()) {
            taskDependency.add(configuration.getBuildArtifacts());
        }
    }

    public Set<Dependency> getDependencies() {
        return dependencies;
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
        dependencies.add(dependency);
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
        return artifacts;
    }

    public Set<PublishArtifact> getAllArtifacts() {
        return Configurations.getArtifacts(this.getHierarchy(), Specs.SATISFIES_ALL);
    }

    public Set<ExcludeRule> getExcludeRules() {
        return excludeRules;
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

    public String getUploadInternalTaskName() {
        return Configurations.uploadInternalTaskName(getName());
    }

    public String getUploadTaskName() {
        return Configurations.uploadTaskName(getName());
    }

    public ProjectDependenciesBuildInstruction getProjectDependenciesBuildInstruction() {
        return projectDependenciesBuildInstruction;
    }

    public void setProjectDependenciesBuildInstruction(ProjectDependenciesBuildInstruction projectDependenciesBuildInstruction) {
        this.projectDependenciesBuildInstruction = projectDependenciesBuildInstruction;
    }

    public IvyService getIvyService() {
        return ivyService;
    }

    public ConfigurationsProvider getConfigurationsProvider() {
        return configurationsProvider;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DefaultConfiguration that = (DefaultConfiguration) o;

        if (name != null ? !name.equals(that.name) : that.name != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    public String getDisplayName() {
        return String.format("configuration '%s'", name);
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
        DefaultConfiguration copiedConfiguration = new DefaultConfiguration("copyOf" + getName(),
                configurationsProvider, ivyService, projectDependenciesBuildInstruction);
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
            copiedConfiguration.getExcludeRules().add(new DefaultExcludeRule(excludeRule.getExcludeArgs()));    
        }

        for (Dependency dependency : dependencies) {
            copiedConfiguration.addDependency(dependency.copy());
        }
        return copiedConfiguration;
    }

    public Configuration copy(Closure dependencySpec) {
        return copy((Spec<Dependency>) DefaultGroovyMethods.asType(dependencySpec, Spec.class));
    }

    public Configuration copyRecursive(Closure dependencySpec) {
        return copyRecursive((Spec<Dependency>) DefaultGroovyMethods.asType(dependencySpec, Spec.class));
    }

    private void throwExceptionIfNotInUnresolvedState() {
        if (state != State.UNRESOLVED) {
            throw new InvalidUserDataException("You can't change a configuration which is not in unresolved state!");
        }
    }

    class ConfigurationFileCollection extends AbstractFileCollection {
        private Spec<Dependency> dependencySpec;

        private ConfigurationFileCollection(Spec<Dependency> dependencySpec) {
            this.dependencySpec = dependencySpec;
        }

        public ConfigurationFileCollection(Closure dependencySpecClosure) {
            this.dependencySpec = (Spec<Dependency>) DefaultGroovyMethods.asType(dependencySpecClosure, Spec.class);
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
            return "ConfigurationFileCollection for " + getName();
        }

        public Set<File> getFiles() {
            ResolvedConfiguration resolvedConfiguration = getResolvedConfiguration();
            if (state == State.RESOLVED_WITH_FAILURES) {
                resolvedConfiguration.rethrowFailure();
            }
            return resolvedConfiguration.getFiles(dependencySpec);
        }
    }
}

