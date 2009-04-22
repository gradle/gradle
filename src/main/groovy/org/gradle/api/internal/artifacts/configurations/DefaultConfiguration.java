package org.gradle.api.internal.artifacts.configurations;

import static org.apache.ivy.core.module.descriptor.Configuration.Visibility;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Task;
import org.gradle.api.artifacts.*;
import org.gradle.api.artifacts.specs.DependencySpecs;
import org.gradle.api.artifacts.specs.Type;
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
    private Set<Configuration> extendsFrom = new HashSet<Configuration>();
    private String description;
    private ConfigurationsProvider configurationsProvider;

    private IvyService ivyService;

    private DependencyMetaDataProvider dependencyMetaDataProvider;

    private Set<Dependency> dependencies = new HashSet<Dependency>();

    private Set<PublishArtifact> artifacts = new LinkedHashSet<PublishArtifact>();

    private ResolverProvider resolverProvider;

    private Set<ExcludeRule> excludeRules = new LinkedHashSet<ExcludeRule>();

    private State state = State.UNRESOLVED;

    private ResolveReport cachedResolveReport = null;

    public DefaultConfiguration(String name, ConfigurationsProvider configurationsProvider, IvyService ivyService,
                                ResolverProvider resolverProvider, DependencyMetaDataProvider dependencyMetaDataProvider) {
        this.name = name;
        this.configurationsProvider = configurationsProvider;
        this.ivyService = ivyService;
        this.resolverProvider = resolverProvider;
        this.dependencyMetaDataProvider = dependencyMetaDataProvider;
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
        ResolveReport report = resolveAsReport();
        if (state == State.RESOLVED_WITH_FAILURES) {
            throw new InvalidUserDataException("Not all dependencies could be resolved!");
        }
        return ivyService.resolveFromReport(this, report);
    }

    public ResolveReport resolveAsReport() {
        if (state == State.UNRESOLVED) {
            cachedResolveReport = ivyService.resolveAsReport(this, dependencyMetaDataProvider.getModule(), dependencyMetaDataProvider.getGradleUserHomeDir(), dependencyMetaDataProvider.getClientModuleRegistry());
            if (cachedResolveReport.hasError()) {
                state = State.RESOLVED_WITH_FAILURES;
            } else {
                state = State.RESOLVED;
            }
        }
        return cachedResolveReport;
    }

    public void publish(List<DependencyResolver> publishResolvers, PublishInstruction publishInstruction) {
        ivyService.publish(new HashSet(getHierarchy()),
                publishInstruction,
                publishResolvers,
                dependencyMetaDataProvider.getModule(),
                dependencyMetaDataProvider.getGradleUserHomeDir());
    }

    public Set<File> getFiles() {
        return new LinkedHashSet<File>(resolve());
    }

    public TaskDependency getBuildDependencies() {
        return new TaskDependency() {
            public Set<? extends Task> getDependencies(Task task) {
                DefaultTaskDependency taskDependency = new DefaultTaskDependency();
                for (Configuration configuration : getExtendsFrom()) {
                    taskDependency.add(configuration.getBuildDependencies());
                }
                for (ProjectDependency projectDependency : getProjectDependencies()) {
                    Configuration configuration = projectDependency.getDependencyProject().getConfigurations().get(
                            projectDependency.getDependencyConfiguration()
                    );
                    taskDependency.add(projectDependency.getDependencyProject().task(configuration.getUploadInternalTaskName()));
                }
                return taskDependency.getDependencies(task);
            }
        };
    }

    public TaskDependency getBuildArtifacts() {
        return new TaskDependency() {
            public Set<? extends Task> getDependencies(Task task) {
                DefaultTaskDependency taskDependency = new DefaultTaskDependency();
                for (Configuration configuration : getExtendsFrom()) {
                    taskDependency.add(configuration.getBuildArtifacts());
                }
                for (PublishArtifact publishArtifact : getArtifacts()) {
                    taskDependency.add(publishArtifact.getTaskDependency());
                }
                return taskDependency.getDependencies(task);
            }
        };
    }

    public Set<Dependency> getDependencies() {
        return dependencies;
    }

    public Set<Dependency> getAllDependencies() {
        return Configurations.getDependencies(this.getHierarchy(), Specs.SATISFIES_ALL);
    }

    public Set<ProjectDependency> getProjectDependencies() {
        Set<Dependency> dependencies = Configurations.getDependencies(WrapUtil.<Configuration>toList(this), DependencySpecs.type(Type.PROJECT));
        Set<ProjectDependency> result = createSetWithGenericProjectDependencyType(dependencies);
        return result;
    }

    public Set<ProjectDependency> getAllProjectDependencies() {
        Set<Dependency> dependencies = Specs.filterIterable(getAllDependencies(), DependencySpecs.type(Type.PROJECT));
        Set<ProjectDependency> result = createSetWithGenericProjectDependencyType(dependencies);
        return result;
    }

    private Set<ProjectDependency> createSetWithGenericProjectDependencyType(Set<Dependency> dependencies) {
        // todo There must be a nicer way of doing this
        Set<ProjectDependency> result = new HashSet<ProjectDependency>();
        for (Dependency dependency : dependencies) {
            result.add((ProjectDependency) dependency);
        }
        return result;
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

    public Set<PublishArtifact> getArtifacts() {
        return artifacts;
    }

    public Set<PublishArtifact> getAllArtifacts() {
        return Configurations.getArtifacts(this.getHierarchy(), Specs.SATISFIES_ALL);
    }

    public DependencyMetaDataProvider getArtifactsProvider() {
        return dependencyMetaDataProvider;
    }

    public List<DependencyResolver> getDependencyResolvers() {
        return resolverProvider.getResolvers();
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
        int result = name != null ? name.hashCode() : 0;
        return result;
    }

    @Override
    public String toString() {
        return "DefaultConfiguration{" +
                "name='" + name + '\'' +
                ", extendsFrom=" + extendsFrom +
                ", description='" + description + '\'' +
                ", visibility=" + visibility +
                '}';
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
                configurationsProvider, ivyService, resolverProvider, dependencyMetaDataProvider);
        configurationsProvider.setTheOnlyConfiguration(copiedConfiguration);
        // state, cachedResolveReport, and extendsFrom intentionally not copied - must re-resolve copy
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

    private void throwExceptionIfNotInUnresolvedState() {
        if (state != State.UNRESOLVED) {
            throw new InvalidUserDataException("You can't change a configuration which is not in unresolved state!");
        }
    }
}
