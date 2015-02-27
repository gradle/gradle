/*
 * Copyright 2011 the original author or authors.
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
import org.gradle.api.Action;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.*;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.CompositeDomainObjectSet;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.api.internal.artifacts.*;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.projectresult.ResolvedProjectConfigurationResult;
import org.gradle.api.internal.file.AbstractFileCollection;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.listener.ClosureBackedMethodInvocationDispatch;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.event.ListenerManager;
import org.gradle.util.CollectionUtils;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.DeprecationLogger;
import org.gradle.util.WrapUtil;

import java.io.File;
import java.util.*;

import static org.apache.ivy.core.module.descriptor.Configuration.Visibility;

public class DefaultConfiguration extends AbstractFileCollection implements ConfigurationInternal {

    private final String path;
    private final String name;

    private Visibility visibility = Visibility.PUBLIC;
    private boolean transitive = true;
    private Set<Configuration> extendsFrom = new LinkedHashSet<Configuration>();
    private String description;
    private ConfigurationsProvider configurationsProvider;
    private final ConfigurationResolver resolver;
    private final ListenerManager listenerManager;
    private final DependencyMetaDataProvider metaDataProvider;
    private final DefaultDependencySet dependencies;
    private final CompositeDomainObjectSet<Dependency> inheritedDependencies;
    private final DefaultDependencySet allDependencies;
    private final DefaultPublishArtifactSet artifacts;
    private final CompositeDomainObjectSet<PublishArtifact> inheritedArtifacts;
    private final DefaultPublishArtifactSet allArtifacts;
    private final ConfigurationResolvableDependencies resolvableDependencies = new ConfigurationResolvableDependencies();
    private final ListenerBroadcast<DependencyResolutionListener> resolutionListenerBroadcast;
    private Set<ExcludeRule> excludeRules = new LinkedHashSet<ExcludeRule>();
    private boolean modified;
    private boolean resolvedWithFailures;

    // This lock only protects the following fields
    private final Object lock = new Object();
    private InternalState state = InternalState.UNOBSERVED;
    private ResolverResults cachedResolverResults;
    private final ResolutionStrategyInternal resolutionStrategy;
    private final ProjectFinder projectFinder;

    public DefaultConfiguration(String path, String name, ConfigurationsProvider configurationsProvider,
                                ConfigurationResolver resolver, ListenerManager listenerManager,
                                DependencyMetaDataProvider metaDataProvider,
                                ResolutionStrategyInternal resolutionStrategy,
                                ProjectFinder projectFinder) {
        this.path = path;
        this.name = name;
        this.configurationsProvider = configurationsProvider;
        this.resolver = resolver;
        this.listenerManager = listenerManager;
        this.metaDataProvider = metaDataProvider;
        this.resolutionStrategy = resolutionStrategy;
        this.projectFinder = projectFinder;

        resolutionListenerBroadcast = listenerManager.createAnonymousBroadcaster(DependencyResolutionListener.class);

        RunnableMutationValidator veto = new RunnableMutationValidator(false) {
            @Override
            public void validateMutation(boolean lenient) {
                DefaultConfiguration.this.validateMutation(lenient);
            }
        };

        DefaultDomainObjectSet<Dependency> ownDependencies = new DefaultDomainObjectSet<Dependency>(Dependency.class);
        ownDependencies.beforeChange(veto);

        dependencies = new DefaultDependencySet(String.format("%s dependencies", getDisplayName()), ownDependencies);
        inheritedDependencies = CompositeDomainObjectSet.create(Dependency.class, ownDependencies);
        allDependencies = new DefaultDependencySet(String.format("%s all dependencies", getDisplayName()), inheritedDependencies);

        DefaultDomainObjectSet<PublishArtifact> ownArtifacts = new DefaultDomainObjectSet<PublishArtifact>(PublishArtifact.class);
        ownArtifacts.beforeChange(veto);
        artifacts = new DefaultPublishArtifactSet(String.format("%s artifacts", getDisplayName()), ownArtifacts);
        inheritedArtifacts = CompositeDomainObjectSet.create(PublishArtifact.class, ownArtifacts);
        allArtifacts = new DefaultPublishArtifactSet(String.format("%s all artifacts", getDisplayName()), inheritedArtifacts);

        resolutionStrategy.beforeChange(veto);
    }

    public String getName() {
        return name;
    }

    @Override
    public InternalState getInternalState() {
        synchronized (lock) {
            return state;
        }
    }

    public State getState() {
        synchronized (lock) {
            if (state == InternalState.RESULTS_RESOLVED || state == InternalState.TASK_DEPENDENCIES_RESOLVED) {
                if (resolvedWithFailures) {
                    return State.RESOLVED_WITH_FAILURES;
                } else {
                    return State.RESOLVED;
                }
            } else {
                return State.UNRESOLVED;
            }
        }
    }

    public ModuleInternal getModule() {
        return metaDataProvider.getModule();
    }

    public boolean isVisible() {
        return visibility == Visibility.PUBLIC;
    }

    public Configuration setVisible(boolean visible) {
        validateMutation(false);
        this.visibility = visible ? Visibility.PUBLIC : Visibility.PRIVATE;
        return this;
    }

    public Set<Configuration> getExtendsFrom() {
        return Collections.unmodifiableSet(extendsFrom);
    }

    public Configuration setExtendsFrom(Iterable<Configuration> extendsFrom) {
        validateMutation(false);
        for (Configuration configuration : this.extendsFrom) {
            inheritedArtifacts.removeCollection(configuration.getAllArtifacts());
            inheritedDependencies.removeCollection(configuration.getAllDependencies());
        }
        this.extendsFrom = new HashSet<Configuration>();
        for (Configuration configuration : extendsFrom) {
            extendsFrom(configuration);
        }
        return this;
    }

    public Configuration extendsFrom(Configuration... extendsFrom) {
        validateMutation(false);
        for (Configuration configuration : extendsFrom) {
            if (configuration.getHierarchy().contains(this)) {
                throw new InvalidUserDataException(String.format(
                        "Cyclic extendsFrom from %s and %s is not allowed. See existing hierarchy: %s", this,
                        configuration, configuration.getHierarchy()));
            }
            this.extendsFrom.add(configuration);
            inheritedArtifacts.addCollection(configuration.getAllArtifacts());
            inheritedDependencies.addCollection(configuration.getAllDependencies());
        }
        return this;
    }

    public boolean isTransitive() {
        return transitive;
    }

    public Configuration setTransitive(boolean transitive) {
        validateMutation(false);
        this.transitive = transitive;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public Configuration setDescription(String description) {
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

    public Set<File> files(Spec<? super Dependency> dependencySpec) {
        return fileCollection(dependencySpec).getFiles();
    }

    public FileCollection fileCollection(Spec<? super Dependency> dependencySpec) {
        return new ConfigurationFileCollection(dependencySpec);
    }

    public FileCollection fileCollection(Closure dependencySpecClosure) {
        return new ConfigurationFileCollection(dependencySpecClosure);
    }

    public FileCollection fileCollection(Dependency... dependencies) {
        return new ConfigurationFileCollection(WrapUtil.toLinkedSet(dependencies));
    }

    public void markAsObserved() {
        synchronized (lock) {
            if (state == InternalState.UNOBSERVED) {
                state = InternalState.OBSERVED;
            }
        }
        for (Configuration configuration : extendsFrom) {
            ((ConfigurationInternal) configuration).markAsObserved();
        }
    }

    public ResolvedConfiguration getResolvedConfiguration() {
        resolveNow(InternalState.RESULTS_RESOLVED);
        return cachedResolverResults.getResolvedConfiguration();
    }

    private void resolveNow(InternalState requestedState) {
        synchronized (lock) {
            boolean needsResolve = state == InternalState.UNOBSERVED || state == InternalState.OBSERVED || modified;
            if (needsResolve) {
                DependencyResolutionListener broadcast = getDependencyResolutionBroadcast();
                ResolvableDependencies incoming = getIncoming();
                broadcast.beforeResolve(incoming);
                cachedResolverResults = resolver.resolve(this);
                for (Configuration configuration : extendsFrom) {
                    ((ConfigurationInternal) configuration).markAsObserved();
                }
                resolvedWithFailures = cachedResolverResults.getResolvedConfiguration().hasError();
                modified = false;
                markAsResolved(requestedState);
                broadcast.afterResolve(incoming);
            } else {
                markAsResolved(requestedState);
            }
        }
    }

    private void markAsResolved(InternalState requestedState) {
        // We can't move back from resolved for results state
        if (state != InternalState.RESULTS_RESOLVED) {
            state = requestedState;
        }
    }

    public TaskDependency getBuildDependencies() {
        DefaultTaskDependency taskDependency = new DefaultTaskDependency();
        taskDependency.add(allDependencies.getBuildDependencies());

        resolveNow(InternalState.TASK_DEPENDENCIES_RESOLVED);
        for (ResolvedProjectConfigurationResult projectResult : cachedResolverResults.getResolvedProjectConfigurationResults().getAllProjectConfigurationResults()) {
            ProjectInternal project = projectFinder.getProject(projectResult.getId().getProjectPath());
            for (String targetConfigName : projectResult.getTargetConfigurations()) {
                Configuration targetConfig = project.getConfigurations().getByName(targetConfigName);
                taskDependency.add(targetConfig);
                taskDependency.add(targetConfig.getAllArtifacts());
            }
        }

        return taskDependency;
    }

    /**
     * {@inheritDoc}
     */
    public TaskDependency getTaskDependencyFromProjectDependency(final boolean useDependedOn, final String taskName) {
        if (useDependedOn) {
            return new TasksFromProjectDependencies(taskName, getAllDependencies());
        } else {
            return new TasksFromDependentProjects(taskName, getName());
        }
    }

    public DependencySet getDependencies() {
        return dependencies;
    }

    public DependencySet getAllDependencies() {
        return allDependencies;
    }

    public PublishArtifactSet getArtifacts() {
        return artifacts;
    }

    public PublishArtifactSet getAllArtifacts() {
        return allArtifacts;
    }

    public Set<ExcludeRule> getExcludeRules() {
        return Collections.unmodifiableSet(excludeRules);
    }

    public void setExcludeRules(Set<ExcludeRule> excludeRules) {
        validateMutation(false);
        this.excludeRules = excludeRules;
    }

    public DefaultConfiguration exclude(Map<String, String> excludeRuleArgs) {
        validateMutation(false);
        excludeRules.add(ExcludeRuleNotationConverter.parser().parseNotation(excludeRuleArgs)); //TODO SF try using ExcludeRuleContainer
        return this;
    }

    public String getUploadTaskName() {
        return Configurations.uploadTaskName(getName());
    }

    public String getDisplayName() {
        return String.format("configuration '%s'", path);
    }

    public ResolvableDependencies getIncoming() {
        return resolvableDependencies;
    }

    public ConfigurationInternal copy() {
        return createCopy(getDependencies(), false);
    }

    public Configuration copyRecursive() {
        return createCopy(getAllDependencies(), true);
    }

    public Configuration copy(Spec<? super Dependency> dependencySpec) {
        return createCopy(CollectionUtils.filter(getDependencies(), dependencySpec), false);
    }

    public Configuration copyRecursive(Spec<? super Dependency> dependencySpec) {
        return createCopy(CollectionUtils.filter(getAllDependencies(), dependencySpec), true);
    }

    private DefaultConfiguration createCopy(Set<Dependency> dependencies, boolean recursive) {
        DetachedConfigurationsProvider configurationsProvider = new DetachedConfigurationsProvider();
        DefaultConfiguration copiedConfiguration = new DefaultConfiguration(path + "Copy", name + "Copy",
                configurationsProvider, resolver, listenerManager, metaDataProvider, resolutionStrategy.copy(), projectFinder);
        configurationsProvider.setTheOnlyConfiguration(copiedConfiguration);
        // state, cachedResolvedConfiguration, and extendsFrom intentionally not copied - must re-resolve copy
        // copying extendsFrom could mess up dependencies when copy was re-resolved

        copiedConfiguration.visibility = visibility;
        copiedConfiguration.transitive = transitive;
        copiedConfiguration.description = description;

        copiedConfiguration.getArtifacts().addAll(getAllArtifacts());

        // todo An ExcludeRule is a value object but we don't enforce immutability for DefaultExcludeRule as strong as we
        // should (we expose the Map). We should provide a better API for ExcludeRule (I don't want to use unmodifiable Map).
        // As soon as DefaultExcludeRule is truly immutable, we don't need to create a new instance of DefaultExcludeRule.
        Set<Configuration> excludeRuleSources = new LinkedHashSet<Configuration>();
        excludeRuleSources.add(this);
        if (recursive) {
            excludeRuleSources.addAll(getHierarchy());
        }

        for (Configuration excludeRuleSource : excludeRuleSources) {
            for (ExcludeRule excludeRule : excludeRuleSource.getExcludeRules()) {
                copiedConfiguration.excludeRules.add(new DefaultExcludeRule(excludeRule.getGroup(), excludeRule.getModule()));
            }
        }

        DomainObjectSet<Dependency> copiedDependencies = copiedConfiguration.getDependencies();
        for (Dependency dependency : dependencies) {
            copiedDependencies.add(dependency.copy());
        }
        return copiedConfiguration;
    }

    public Configuration copy(Closure dependencySpec) {
        return copy(Specs.<Dependency>convertClosureToSpec(dependencySpec));
    }

    public Configuration copyRecursive(Closure dependencySpec) {
        return copyRecursive(Specs.<Dependency>convertClosureToSpec(dependencySpec));
    }

    public DependencyResolutionListener getDependencyResolutionBroadcast() {
        return resolutionListenerBroadcast.getSource();
    }

    public ResolutionStrategyInternal getResolutionStrategy() {
        return resolutionStrategy;
    }

    public String getPath() {
        return path;
    }

    public Configuration resolutionStrategy(Closure closure) {
        ConfigureUtil.configure(closure, resolutionStrategy);
        return this;
    }

    private void validateMutation(boolean lenient) {
        switch (state) {
            case UNOBSERVED:
                // Nothing from the configuration has been observed yet, can change anything.
                break;
            case OBSERVED:
                // The configuration has been used in a resolution, and it is deprecated for
                // build logic to change any dependencies, artifacts, exclude rules or parent
                // configurations (non-lenient properties).
                if (!lenient) {
                    DeprecationLogger.nagUserOfDeprecatedBehaviour(String.format("Attempting to change %s after it has been included in dependency resolution", getDisplayName()));
                }
                break;
            case TASK_DEPENDENCIES_RESOLVED:
                // The task dependencies for the configuration have been calculated using
                // Configuration.getBuildDependencies(). It is deprecated for build logic to
                // change anything about the configuration.
                DeprecationLogger.nagUserOfDeprecatedBehaviour(String.format("Attempting to change %s after it has been resolved", getDisplayName()));
                break;
            case RESULTS_RESOLVED:
                // The public result for the configuration has been calculated. It is an
                // error to change non-lenient properties, and deprecated to change anything else
                // about the configuration.
                // TODO Add logic from PR#409 for deprecated changes
                if (lenient) {
                    DeprecationLogger.nagUserOfDeprecatedBehaviour(String.format("Attempting to change %s after it has been resolved", getDisplayName()));
                } else {
                    throw new InvalidUserDataException(String.format("Cannot change %s after it has been resolved.", getDisplayName()));
                }
        }
        modified = true;
    }

    class ConfigurationFileCollection extends AbstractFileCollection {
        private Spec<? super Dependency> dependencySpec;

        private ConfigurationFileCollection(Spec<? super Dependency> dependencySpec) {
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

        @Override
        public TaskDependency getBuildDependencies() {
            return DefaultConfiguration.this.getBuildDependencies();
        }

        public Spec<? super Dependency> getDependencySpec() {
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

    /**
     * Print a formatted representation of a Configuration
     */
    public String dump() {
        StringBuilder reply = new StringBuilder();

        reply.append("\nConfiguration:");
        reply.append("  class='" + this.getClass() + "'");
        reply.append("  name='" + this.getName() + "'");
        reply.append("  hashcode='" + this.hashCode() + "'");

        reply.append("\nLocal Dependencies:");
        if (getDependencies().size() > 0) {
            for (Dependency d : getDependencies()) {
                reply.append("\n   " + d);
            }
        } else {
            reply.append("\n   none");
        }

        reply.append("\nLocal Artifacts:");
        if (getArtifacts().size() > 0) {
            for (PublishArtifact a : getArtifacts()) {
                reply.append("\n   " + a);
            }
        } else {
            reply.append("\n   none");
        }

        reply.append("\nAll Dependencies:");
        if (getAllDependencies().size() > 0) {
            for (Dependency d : getAllDependencies()) {
                reply.append("\n   " + d);
            }
        } else {
            reply.append("\n   none");
        }


        reply.append("\nAll Artifacts:");
        if (getAllArtifacts().size() > 0) {
            for (PublishArtifact a : getAllArtifacts()) {
                reply.append("\n   " + a);
            }
        } else {
            reply.append("\n   none");
        }

        return reply.toString();
    }

    private class ConfigurationResolvableDependencies implements ResolvableDependencies {
        public String getName() {
            return name;
        }

        public String getPath() {
            return path;
        }

        @Override
        public String toString() {
            return String.format("dependencies '%s'", path);
        }

        public FileCollection getFiles() {
            return DefaultConfiguration.this.fileCollection(Specs.<Dependency>satisfyAll());
        }

        public DependencySet getDependencies() {
            return getAllDependencies();
        }

        public void beforeResolve(Action<? super ResolvableDependencies> action) {
            resolutionListenerBroadcast.add("beforeResolve", action);
        }

        public void beforeResolve(Closure action) {
            resolutionListenerBroadcast.add(new ClosureBackedMethodInvocationDispatch("beforeResolve", action));
        }

        public void afterResolve(Action<? super ResolvableDependencies> action) {
            resolutionListenerBroadcast.add("afterResolve", action);
        }

        public void afterResolve(Closure action) {
            resolutionListenerBroadcast.add(new ClosureBackedMethodInvocationDispatch("afterResolve", action));
        }

        public ResolutionResult getResolutionResult() {
            DefaultConfiguration.this.resolveNow(InternalState.RESULTS_RESOLVED);
            return DefaultConfiguration.this.cachedResolverResults.getResolutionResult();
        }
    }

}
