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
import org.gradle.api.internal.file.AbstractFileCollection;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.listener.ClosureBackedMethodInvocationDispatch;
import org.gradle.listener.ListenerBroadcast;
import org.gradle.listener.ListenerManager;
import org.gradle.util.CollectionUtils;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.DeprecationLogger;
import org.gradle.util.WrapUtil;
import org.gradle.util.Path;

import java.io.File;
import java.util.*;

import static org.apache.ivy.core.module.descriptor.Configuration.Visibility;

public class DefaultConfiguration extends AbstractFileCollection implements ConfigurationInternal {

    private final String path;
    private final String name;

    private Visibility visibility = Visibility.PUBLIC;
    private boolean transitive = true;
    private Set<Configuration> extendsFrom = new LinkedHashSet<Configuration>();
    private Set<Configuration> extendsFromExternal = new LinkedHashSet<Configuration>();
    private String description;
    private final ConfigurationsProvider configurationsProvider;
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

    // This lock only protects the following fields
    private final Object lock = new Object();
    private State state = State.UNRESOLVED;
    private boolean includedInResult;
    private ResolverResults cachedResolverResults;
    private final ResolutionStrategyInternal resolutionStrategy;
    private final String projectPath;

    public DefaultConfiguration(String path, final String name, final ConfigurationsProvider configurationsProvider,
                                        final ConfigurationResolver resolver, final ListenerManager listenerManager, final DependencyMetaDataProvider metaDataProvider,
                                        final ResolutionStrategyInternal resolutionStrategy) {
        this.path = path;
        this.name = name;
        this.configurationsProvider = configurationsProvider;
        this.resolver = resolver;
        this.listenerManager = listenerManager;
        this.metaDataProvider = metaDataProvider;
        this.resolutionStrategy = resolutionStrategy;

        resolutionListenerBroadcast = listenerManager.createAnonymousBroadcaster(DependencyResolutionListener.class);

        VetoContainerChangeAction veto = new VetoContainerChangeAction();

        DefaultDomainObjectSet<Dependency> ownDependencies = new DefaultDomainObjectSet<Dependency>(Dependency.class);
        ownDependencies.beforeChange(veto);

        dependencies = new DefaultDependencySet(String.format("%s dependencies", getDisplayName()), ownDependencies);
        inheritedDependencies = CompositeDomainObjectSet.create(Dependency.class, ownDependencies);
        allDependencies = new DefaultDependencySet(String.format("%s all dependencies", getDisplayName()), inheritedDependencies);

        DefaultDomainObjectSet<PublishArtifact> ownArtifacts = new DefaultDomainObjectSet<PublishArtifact>(PublishArtifact.class);
        ownArtifacts.beforeChange(new VetoContainerChangeAction());
        artifacts = new DefaultPublishArtifactSet(String.format("%s artifacts", getDisplayName()), ownArtifacts);
        inheritedArtifacts = CompositeDomainObjectSet.create(PublishArtifact.class, ownArtifacts);
        allArtifacts = new DefaultPublishArtifactSet(String.format("%s all artifacts", getDisplayName()), inheritedArtifacts);
        Path parentPath = null;
        if (this.path != null && this.path.startsWith(":")) {
            parentPath = new Path(this.path).getParent();
        }
        this.projectPath = parentPath != null ? parentPath.getPath() : null;
    }

    public String getName() {
        return name;
    }

    public State getState() {
        synchronized (lock) {
            return state;
        }
    }

    public ModuleInternal getModule() {
        return metaDataProvider.getModule();
    }

    public boolean isVisible() {
        return visibility == Visibility.PUBLIC;
    }

    public Configuration setVisible(boolean visible) {
        validateMutation();
        this.visibility = visible ? Visibility.PUBLIC : Visibility.PRIVATE;
        return this;
    }

    public Set<Configuration> getExtendsFrom() {
        return Collections.unmodifiableSet(extendsFrom);
    }

    public Set<Configuration> getExtendsFromExternal() {
        return Collections.unmodifiableSet(extendsFromExternal);
    }

    public Configuration setExtendsFrom(Iterable<Configuration> extendsFrom) {
        validateMutation();
        for (Configuration configuration: this.extendsFrom) {
            inheritedArtifacts.removeCollection(configuration.getAllArtifacts());
            inheritedDependencies.removeCollection(configuration.getAllDependencies());
        }
        for (Configuration configuration: extendsFromExternal) {
            inheritedArtifacts.removeCollection(configuration.getAllArtifacts());
            inheritedDependencies.removeCollection(configuration.getAllDependencies());
        }
        extendsFromExternal = new HashSet<Configuration>();
        this.extendsFrom = new HashSet<Configuration>();
        for (Configuration configuration: extendsFrom) {
            extendsFrom(configuration);
        }
        return this;
    }

    public Configuration extendsFrom(Configuration... extendsFrom) {
        validateMutation();
        if (getProjectPath() == null) {
            throw new InvalidUserDataException("Cannot extend a detached configuration.");
        }
        for (Configuration configuration: extendsFrom) {
            if (configuration.getCompleteHierarchy().contains(this)) {
                throw new InvalidUserDataException(String.format(
                        "Cyclic extendsFrom from %s and %s is not allowed. See existing hierarchy: %s", this,
                        configuration, configuration.getHierarchy()));
            }
            final ConfigurationInternal internalConfiguration = (ConfigurationInternal) configuration;
            if (internalConfiguration.getProjectPath() == null) {
                throw new InvalidUserDataException(
                        String.format("The configuration '%s' is detached, cannot extend from it.", configuration));
            }

            // Note: projectPath may not be null throughout the configuration is not detached!
            if (!projectPath.equals(internalConfiguration.getProjectPath())) {
                // Since this configuration is part of a project different from the one this is attached to,
                // we need to ensure the project is evaluated first (dependencies, configuration extensions, etc.)
                configurationsProvider.ensureProjectIsEvaluated(internalConfiguration.getProjectPath());
                // After this we add the extends to the external set
                extendsFromExternal.add(configuration);
            } else {
                // If this is the configuration, which is part of the same project, add it to the internal extensions
                this.extendsFrom.add(configuration);
            }
            inheritedArtifacts.addCollection(configuration.getAllArtifacts());
            inheritedDependencies.addCollection(configuration.getAllDependencies());
        }
        return this;
    }

    public boolean isTransitive() {
        return transitive;
    }

    public Configuration setTransitive(boolean transitive) {
        validateMutation();
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

    public Set<Configuration> getCompleteHierarchy() {
        Set<Configuration> result = getHierarchy();
        collectSuperExternalConfigs(this, result);
        return result;
    }

    private void collectSuperExternalConfigs(Configuration configuration, final Set<Configuration> result) {
        for (Configuration superConfig: configuration.getExtendsFromExternal()) {
            if (result.contains(superConfig)) {
                result.remove(superConfig);
            }
            result.add(superConfig);
            collectSuperExternalConfigs(superConfig, result);
        }
    }

    private void collectSuperConfigs(Configuration configuration, final Set<Configuration> result) {
        for (Configuration superConfig: configuration.getExtendsFrom()) {
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

    public Set<File> files(@SuppressWarnings("rawtypes") final Closure dependencySpecClosure) {
        return fileCollection(dependencySpecClosure).getFiles();
    }

    public Set<File> files(Spec<? super Dependency> dependencySpec) {
        return fileCollection(dependencySpec).getFiles();
    }

    public FileCollection fileCollection(Spec<? super Dependency> dependencySpec) {
        return new ConfigurationFileCollection(dependencySpec);
    }

    public FileCollection fileCollection(@SuppressWarnings("rawtypes") final Closure dependencySpecClosure) {
        return new ConfigurationFileCollection(dependencySpecClosure);
    }

    public FileCollection fileCollection(Dependency... dependencies) {
        return new ConfigurationFileCollection(WrapUtil.toLinkedSet(dependencies));
    }

    public void includedInResolveResult() {
        this.includedInResult = true;
        for (Configuration configuration: this.extendsFrom) {
            ((ConfigurationInternal) configuration).includedInResolveResult();
        }
    }

    public ResolvedConfiguration getResolvedConfiguration() {
        resolveNow();
        return cachedResolverResults.getResolvedConfiguration();
    }

    private void resolveNow() {
        synchronized (lock) {
            if (state == State.UNRESOLVED) {
                final DependencyResolutionListener broadcast = getDependencyResolutionBroadcast();
                final ResolvableDependencies incoming = getIncoming();
                broadcast.beforeResolve(incoming);
                this.cachedResolverResults = this.resolver.resolve(this);
                for (Configuration configuration: this.extendsFrom) {
                    ((ConfigurationInternal) configuration).includedInResolveResult();
                }
                for (Configuration configuration: this.extendsFromExternal) {
                    ((ConfigurationInternal) configuration).includedInResolveResult();
                }
                if (this.cachedResolverResults.getResolvedConfiguration().hasError()) {
                    state = State.RESOLVED_WITH_FAILURES;
                } else {
                    state = State.RESOLVED;
                }
                broadcast.afterResolve(incoming);
            }
        }
    }

    public TaskDependency getBuildDependencies() {
        return allDependencies.getBuildDependencies();
    }

    /**
     * {@inheritDoc}
     */
    public TaskDependency getTaskDependencyFromProjectDependency(boolean useDependedOn, final String taskName) {
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
        return Collections.unmodifiableSet(this.excludeRules);
    }

    public void setExcludeRules(Set<ExcludeRule> excludeRules) {
        validateMutation();
        this.excludeRules = excludeRules;
    }

    public DefaultConfiguration exclude(Map<String, String> excludeRuleArgs) {
        validateMutation();
        excludeRules.add(ExcludeRuleNotationConverter.parser().parseNotation(excludeRuleArgs)); //TODO SF try using ExcludeRuleContainer
        return this;
    }

    public String getUploadTaskName() {
        return Configurations.uploadTaskName(getName());
    }

    public String getDisplayName() {
        return String.format("configuration '%s'", getPath());
    }

    public ResolvableDependencies getIncoming() {
        return resolvableDependencies;
    }

    public Configuration copy() {
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

    private DefaultConfiguration createCopy(Set<Dependency> dependencies, final boolean recursive) {
        final DetachedConfigurationsProvider configurationsProvider = new DetachedConfigurationsProvider();
        final DefaultConfiguration copiedConfiguration = new DefaultConfiguration(this.path, this.name + "Copy", configurationsProvider,
                this.resolver, this.listenerManager, this.metaDataProvider, this.resolutionStrategy.copy());
        configurationsProvider.setTheOnlyConfiguration(copiedConfiguration);
        // state, cachedResolvedConfiguration, and extendsFrom intentionally not
        // copied - must re-resolve copy
        // copying extendsFrom could mess up dependencies when copy was
        // re-resolved

        copiedConfiguration.visibility = this.visibility;
        copiedConfiguration.transitive = this.transitive;
        copiedConfiguration.description = this.description;

        copiedConfiguration.getArtifacts().addAll(getAllArtifacts());

        // todo An ExcludeRule is a value object but we don't enforce
        // immutability for DefaultExcludeRule as strong as we
        // should (we expose the Map). We should provide a better API for
        // ExcludeRule (I don't want to use unmodifiable Map).
        // As soon as DefaultExcludeRule is truly immutable, we don't need to
        // create a new instance of DefaultExcludeRule.
        final Set<Configuration> excludeRuleSources = new LinkedHashSet<Configuration>();
        excludeRuleSources.add(this);
        if (recursive) {
            excludeRuleSources.addAll(getCompleteHierarchy());
        }

        for (Configuration excludeRuleSource: excludeRuleSources) {
            for (ExcludeRule excludeRule: excludeRuleSource.getExcludeRules()) {
                copiedConfiguration.excludeRules.add(new DefaultExcludeRule(excludeRule.getGroup(), excludeRule.getModule()));
            }
        }

        final DomainObjectSet<Dependency> copiedDependencies = copiedConfiguration.getDependencies();
        for (Dependency dependency: dependencies) {
            copiedDependencies.add(dependency.copy());
        }
        return copiedConfiguration;
    }

    public Configuration copy(@SuppressWarnings("rawtypes") final Closure dependencySpec) {
        return copy(Specs.<Dependency>convertClosureToSpec(dependencySpec));
    }

    public Configuration copyRecursive(@SuppressWarnings("rawtypes") final Closure dependencySpec) {
        return copyRecursive(Specs.<Dependency>convertClosureToSpec(dependencySpec));
    }

    public DependencyResolutionListener getDependencyResolutionBroadcast() {
        return resolutionListenerBroadcast.getSource();
    }

    public ResolutionStrategyInternal getResolutionStrategy() {
        return resolutionStrategy;
    }

    public String getPath() {
        return path != null ? this.path : this.name;
    }

    public Configuration resolutionStrategy(@SuppressWarnings("rawtypes") final Closure closure) {
        ConfigureUtil.configure(closure, this.resolutionStrategy);
        return this;
    }

    public String getProjectPath() {
        return projectPath;
    }

    private void validateMutation() {
        if (getState() != State.UNRESOLVED) {
            throw new InvalidUserDataException(String.format("Cannot change %s after it has been resolved.", getDisplayName()));
        }
        if (this.includedInResult) {
            DeprecationLogger.nagUserOfDeprecatedBehaviour(String.format("Attempting to change %s after it has been included in dependency resolution",
                    getDisplayName()));
        }
    }

    class ConfigurationFileCollection extends AbstractFileCollection {
        private Spec<? super Dependency> dependencySpec;

        private ConfigurationFileCollection(Spec<? super Dependency> dependencySpec) {
            this.dependencySpec = dependencySpec;
        }

        public ConfigurationFileCollection(@SuppressWarnings("rawtypes") final Closure dependencySpecClosure) {
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
            synchronized (DefaultConfiguration.this.lock) {
                final ResolvedConfiguration resolvedConfiguration = getResolvedConfiguration();
                if (getState() == State.RESOLVED_WITH_FAILURES) {
                    resolvedConfiguration.rethrowFailure();
                }
                return resolvedConfiguration.getFiles(this.dependencySpec);
            }
        }
    }

    /**
     * Print a formatted representation of a Configuration
     */
    public String dump() {
        final StringBuilder reply = new StringBuilder();

        reply.append("\nConfiguration:");
        reply.append("  class='" + this.getClass() + "'");
        reply.append("  name='" + this.getName() + "'");
        reply.append("  hashcode='" + this.hashCode() + "'");

        reply.append("\nLocal Dependencies:");
        if (getDependencies().size() > 0) {
            for (Dependency d: getDependencies()) {
                reply.append("\n   " + d);
            }
        } else {
            reply.append("\n   none");
        }

        reply.append("\nLocal Artifacts:");
        if (getArtifacts().size() > 0) {
            for (PublishArtifact a: getArtifacts()) {
                reply.append("\n   " + a);
            }
        } else {
            reply.append("\n   none");
        }


        reply.append("\nAll Dependencies:");
        if (getAllDependencies().size() > 0) {
            for (Dependency d: getAllDependencies()) {
                reply.append("\n   " + d);
            }
        } else {
            reply.append("\n   none");
        }

        reply.append("\nAll Artifacts:");
        if (getAllArtifacts().size() > 0) {
            for (PublishArtifact a: getAllArtifacts()) {
                reply.append("\n   " + a);
            }
        } else {
            reply.append("\n   none");
        }

        return reply.toString();
    }

    private class VetoContainerChangeAction implements Runnable {
        public void run() {
            validateMutation();
        }
    }

    private class ConfigurationResolvableDependencies implements ResolvableDependencies {
        public String getName() {
            return DefaultConfiguration.this.name;
        }

        public String getPath() {
            return DefaultConfiguration.this.getPath();
        }

        @Override
        public String toString() {
            return String.format("dependencies '%s'", DefaultConfiguration.this.getPath());
        }

        public FileCollection getFiles() {
            return DefaultConfiguration.this.fileCollection(Specs.<Dependency>satisfyAll());
        }

        public DependencySet getDependencies() {
            return getAllDependencies();
        }

        public void beforeResolve(Action<? super ResolvableDependencies> action) {
            DefaultConfiguration.this.resolutionListenerBroadcast.add("beforeResolve", action);
        }

        public void beforeResolve(@SuppressWarnings("rawtypes") final Closure action) {
            DefaultConfiguration.this.resolutionListenerBroadcast.add(new ClosureBackedMethodInvocationDispatch("beforeResolve", action));
        }

        public void afterResolve(Action<? super ResolvableDependencies> action) {
            DefaultConfiguration.this.resolutionListenerBroadcast.add("afterResolve", action);
        }

        public void afterResolve(@SuppressWarnings("rawtypes") final Closure action) {
            DefaultConfiguration.this.resolutionListenerBroadcast.add(new ClosureBackedMethodInvocationDispatch("afterResolve", action));
        }

        public ResolutionResult getResolutionResult() {
            DefaultConfiguration.this.resolveNow();
            return DefaultConfiguration.this.cachedResolverResults.getResolutionResult();
        }
    }

}