/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.component.external.model.ivy;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.internal.component.ResolutionFailureHandler;
import org.gradle.internal.component.external.descriptor.Artifact;
import org.gradle.internal.component.external.model.ExternalDependencyDescriptor;
import org.gradle.internal.component.model.ComponentGraphResolveState;
import org.gradle.internal.component.model.ConfigurationGraphResolveState;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.GraphVariantSelectionResult;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.VariantGraphResolveState;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Represents a dependency as represented in an Ivy module descriptor file.
 */
public class IvyDependencyDescriptor extends ExternalDependencyDescriptor {
    private final ModuleComponentSelector selector;
    private final String dynamicConstraintVersion;
    private final boolean changing;
    private final boolean transitive;
    private final boolean optional;
    private final SetMultimap<String, String> confs;
    private final List<Exclude> excludes;
    private final List<Artifact> dependencyArtifacts;

    public IvyDependencyDescriptor(ModuleComponentSelector selector, String dynamicConstraintVersion, boolean changing, boolean transitive, boolean optional, Multimap<String, String> confMappings, List<Artifact> artifacts, List<Exclude> excludes) {
        this.selector = selector;
        this.dynamicConstraintVersion = dynamicConstraintVersion;
        this.changing = changing;
        this.transitive = transitive;
        this.optional = optional;
        this.confs = ImmutableSetMultimap.copyOf(confMappings);
        dependencyArtifacts = ImmutableList.copyOf(artifacts);
        this.excludes = ImmutableList.copyOf(excludes);
    }

    public IvyDependencyDescriptor(ModuleComponentSelector requested, ListMultimap<String, String> confMappings) {
        this(requested, requested.getVersion(), false, true, false, confMappings, Collections.emptyList(), Collections.emptyList());
    }

    @Override
    public String toString() {
        return "dependency: " + getSelector() + ", confs: " + confs;
    }

    @Override
    public ModuleComponentSelector getSelector() {
        return selector;
    }

    @Override
    public boolean isOptional() {
        return optional;
    }

    @Override
    public boolean isConstraint() {
        return false;
    }

    @Override
    public boolean isChanging() {
        return changing;
    }

    @Override
    public boolean isTransitive() {
        return transitive;
    }

    public String getDynamicConstraintVersion() {
        return dynamicConstraintVersion;
    }

    public SetMultimap<String, String> getConfMappings() {
        return confs;
    }

    @Override
    protected IvyDependencyDescriptor withRequested(ModuleComponentSelector newRequested) {
        return new IvyDependencyDescriptor(newRequested, dynamicConstraintVersion, changing, transitive, isOptional(), confs, getDependencyArtifacts(), excludes);
    }

    /**
     * Choose a set of configurations from the target component.
     * The set chosen is based on a) the name of the configuration that declared this dependency and b) the {@link #confs} mapping for this dependency.
     *
     * The `confs` mapping is structured as `fromConfiguration -&gt; [targetConf...]`. Targets are collected for all configurations in the `fromConfiguration` hierarchy.
     *   - '*' is a wildcard key, that matches _all_ `fromConfiguration values.
     *       - '*, !A' is a key that matches _all_ `fromConfiguration values _except_ 'A'.
     *   - '%' is a key that matches a `fromConfiguration` value that is not matched by any of the other keys.
     *   - '@' and '#' are special values for matching target configurations. See <a href="http://ant.apache.org/ivy/history/latest-milestone/ivyfile/dependency.html">the Ivy docs</a> for details.
     */
    public GraphVariantSelectionResult selectLegacyConfigurations(ConfigurationMetadata fromConfiguration, ComponentGraphResolveState targetComponent, ResolutionFailureHandler resolutionFailureHandler) {
        // TODO - all this matching stuff is constant for a given DependencyMetadata instance
        List<ConfigurationGraphResolveState> targets = new LinkedList<>();
        boolean matched = false;
        String fromConfigName = fromConfiguration.getName();
        for (String config : fromConfiguration.getHierarchy()) {
            if (confs.containsKey(config)) {
                Set<String> targetPatterns = confs.get(config);
                if (!targetPatterns.isEmpty()) {
                    matched = true;
                }
                for (String targetPattern : targetPatterns) {
                    findMatches(targetComponent, fromConfigName, config, targetPattern, targets, resolutionFailureHandler);
                }
            }
        }
        if (!matched && confs.containsKey("%")) {
            for (String targetPattern : confs.get("%")) {
                findMatches(targetComponent, fromConfigName, fromConfigName, targetPattern, targets, resolutionFailureHandler);
            }
        }

        // TODO - this is not quite right, eg given *,!A->A;*,!B->B the result should be B->A and A->B but will in fact be B-> and A->
        Set<String> wildcardPatterns = confs.get("*");
        if (!wildcardPatterns.isEmpty()) {
            boolean excludeWildcards = false;
            for (String confName : fromConfiguration.getHierarchy()) {
                if (confs.containsKey("!" + confName)) {
                    excludeWildcards = true;
                    break;
                }
            }
            if (!excludeWildcards) {
                for (String targetPattern : wildcardPatterns) {
                    findMatches(targetComponent, fromConfigName, fromConfigName, targetPattern, targets, resolutionFailureHandler);
                }
            }
        }

        ImmutableList.Builder<VariantGraphResolveState> builder = ImmutableList.builderWithExpectedSize(targets.size());
        for (ConfigurationGraphResolveState target : targets) {
            builder.add(target.asVariant());
        }

        return new GraphVariantSelectionResult(builder.build(), false);
    }

    private void findMatches(ComponentGraphResolveState targetComponent, String fromConfiguration, String patternConfiguration, String targetPattern, List<ConfigurationGraphResolveState> targetConfigurations, ResolutionFailureHandler resolutionFailureHandler) {
        int startFallback = targetPattern.indexOf('(');
        if (startFallback >= 0) {
            if (targetPattern.endsWith(")")) {
                String preferred = targetPattern.substring(0, startFallback);
                ConfigurationGraphResolveState configuration = targetComponent.getConfiguration(preferred);
                if (configuration != null) {
                    maybeAddConfiguration(targetConfigurations, configuration);
                    return;
                }
                targetPattern = targetPattern.substring(startFallback + 1, targetPattern.length() - 1);
            }
        }

        if (targetPattern.equals("*")) {
            for (String targetName : targetComponent.getConfigurationNames()) {
                ConfigurationGraphResolveState configuration = targetComponent.getConfiguration(targetName);
                if (configuration.getMetadata().isVisible()) {
                    maybeAddConfiguration(targetConfigurations, configuration);
                }
            }
            return;
        }

        if (targetPattern.equals("@")) {
            targetPattern = patternConfiguration;
        } else if (targetPattern.equals("#")) {
            targetPattern = fromConfiguration;
        }

        ConfigurationGraphResolveState configuration = targetComponent.getConfiguration(targetPattern);
        if (configuration == null) {
            throw resolutionFailureHandler.externalConfigurationNotFoundFailure(fromConfiguration, targetComponent.getId(), targetPattern);
        }
        maybeAddConfiguration(targetConfigurations, configuration);
    }

    private void maybeAddConfiguration(List<ConfigurationGraphResolveState> configurations, ConfigurationGraphResolveState toAdd) {
        Iterator<ConfigurationGraphResolveState> iter = configurations.iterator();
        while (iter.hasNext()) {
            ConfigurationGraphResolveState configuration = iter.next();
            if (configuration.getMetadata().getHierarchy().contains(toAdd.getName())) {
                // this configuration is a child of toAdd, so no need to add it
                return;
            }
            if (toAdd.getMetadata().getHierarchy().contains(configuration.getName())) {
                // toAdd is a child, so implies this configuration
                iter.remove();
            }
        }
        configurations.add(toAdd);
    }

    public List<Exclude> getAllExcludes() {
        return excludes;
    }

    public List<ExcludeMetadata> getConfigurationExcludes(Collection<String> configurations) {
        if (excludes.isEmpty()) {
            return ImmutableList.of();
        }
        ImmutableList.Builder<ExcludeMetadata> rules = ImmutableList.builderWithExpectedSize(excludes.size());
        for (Exclude exclude : excludes) {
            Set<String> ruleConfigurations = exclude.getConfigurations();
            if (include(ruleConfigurations, configurations)) {
                rules.add(exclude);
            }
        }
        return rules.build();
    }

    public List<Artifact> getDependencyArtifacts() {
        return dependencyArtifacts;
    }

    public ImmutableList<IvyArtifactName> getConfigurationArtifacts(ConfigurationMetadata fromConfiguration) {
        if (dependencyArtifacts.isEmpty()) {
            return ImmutableList.of();
        }

        Collection<String> includedConfigurations = fromConfiguration.getHierarchy();
        ImmutableList.Builder<IvyArtifactName> artifacts = ImmutableList.builder();
        for (Artifact depArtifact : dependencyArtifacts) {
            Set<String> artifactConfigurations = depArtifact.getConfigurations();
            if (include(artifactConfigurations, includedConfigurations)) {
                IvyArtifactName ivyArtifactName = depArtifact.getArtifactName();
                artifacts.add(ivyArtifactName);
            }
        }
        return artifacts.build();
    }

    protected static boolean include(Iterable<String> configurations, Collection<String> acceptedConfigurations) {
        for (String configuration : configurations) {
            if (configuration.equals("*")) {
                return true;
            }
            if (acceptedConfigurations.contains(configuration)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        IvyDependencyDescriptor that = (IvyDependencyDescriptor) o;
        return changing == that.changing
            && transitive == that.transitive
            && optional == that.optional
            && Objects.equal(selector, that.selector)
            && Objects.equal(dynamicConstraintVersion, that.dynamicConstraintVersion)
            && Objects.equal(confs, that.confs)
            && Objects.equal(excludes, that.excludes)
            && Objects.equal(dependencyArtifacts, that.dependencyArtifacts);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(selector,
            dynamicConstraintVersion,
            changing,
            transitive,
            optional,
            confs,
            excludes,
            dependencyArtifacts);
    }
}
