/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.internal.component.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusion;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions;
import org.gradle.internal.component.external.descriptor.Artifact;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.local.model.DefaultProjectDependencyMetadata;
import org.gradle.util.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class DefaultDependencyMetadata implements DependencyMetadata {
    private final ModuleVersionSelector requested;
    private final SetMultimap<String, String> confs;
    private final Set<IvyArtifactName> artifacts;
    private final List<Artifact> dependencyArtifacts;
    private final List<Exclude> excludes;

    private final boolean changing;
    private final boolean transitive;
    private final boolean force;
    private final String dynamicConstraintVersion;

    public DefaultDependencyMetadata(ModuleVersionSelector requested, String dynamicConstraintVersion, boolean force, boolean changing, boolean transitive, Multimap<String, String> confMappings, List<Artifact> artifacts, List<Exclude> excludes) {
        this.requested = requested;
        this.changing = changing;
        this.transitive = transitive;
        this.force = force;
        this.dynamicConstraintVersion = dynamicConstraintVersion;
        this.confs = ImmutableSetMultimap.copyOf(confMappings);
        dependencyArtifacts = ImmutableList.copyOf(artifacts);
        this.artifacts = map(dependencyArtifacts);
        this.excludes = ImmutableList.copyOf(excludes);
    }

    public DefaultDependencyMetadata(ModuleVersionIdentifier moduleVersionIdentifier) {
        this(
            new DefaultModuleVersionSelector(moduleVersionIdentifier.getGroup(), moduleVersionIdentifier.getName(), moduleVersionIdentifier.getVersion()),
            ImmutableSetMultimap.<String, String>of(),
            ImmutableList.<Artifact>of(),
            ImmutableList.<Exclude>of(),
            moduleVersionIdentifier.getVersion(),
            false,
            true
        );
    }

    public DefaultDependencyMetadata(ModuleComponentIdentifier componentIdentifier) {
        this(
            new DefaultModuleVersionSelector(componentIdentifier.getGroup(), componentIdentifier.getModule(), componentIdentifier.getVersion()),
            ImmutableSetMultimap.<String, String>of(),
            ImmutableList.<Artifact>of(),
            ImmutableList.<Exclude>of(),
            componentIdentifier.getVersion(),
            false,
            true
        );
    }

    protected DefaultDependencyMetadata(ModuleVersionSelector requested, SetMultimap<String, String> confs,
                                     List<Artifact> dependencyArtifacts, List<Exclude> excludes,
                                     String dynamicConstraintVersion, boolean changing, boolean transitive) {
        this.requested = requested;
        this.confs = confs;
        this.dependencyArtifacts = dependencyArtifacts;
        this.artifacts = map(dependencyArtifacts);
        this.excludes = excludes;
        this.changing = changing;
        this.transitive = transitive;
        this.dynamicConstraintVersion = dynamicConstraintVersion;
        this.force = false;
    }

    private static Set<IvyArtifactName> map(List<Artifact> dependencyArtifacts) {
        if (dependencyArtifacts.isEmpty()) {
            return ImmutableSet.of();
        }
        Set<IvyArtifactName> result = Sets.newLinkedHashSetWithExpectedSize(dependencyArtifacts.size());
        for (Artifact artifact : dependencyArtifacts) {
            result.add(artifact.getArtifactName());
        }
        return result;
    }

    @Override
    public String toString() {
        return "dependency: " + requested + ", confs: " + confs;
    }

    @Override
    public ModuleVersionSelector getRequested() {
        return requested;
    }

    @Override
    public String[] getModuleConfigurations() {
        return confs.keySet().toArray(new String[confs.keySet().size()]);
    }

    @Override
    public Set<ConfigurationMetadata> selectConfigurations(ComponentResolveMetadata fromComponent, ConfigurationMetadata fromConfiguration, ComponentResolveMetadata targetComponent) {
        // TODO - all this matching stuff is constant for a given DependencyMetadata instance
        Set<ConfigurationMetadata> targets = Sets.newLinkedHashSet();
        boolean matched = false;
        String fromConfigName = fromConfiguration.getName();
        for (String config : fromConfiguration.getHierarchy()) {
            Set<String> targetPatterns = confs.get(config);
            if (!targetPatterns.isEmpty()) {
                matched = true;
            }
            for (String targetPattern : targetPatterns) {
                findMatches(fromComponent, targetComponent, fromConfigName, config, targetPattern, targets);
            }
        }
        if (!matched) {
            for (String targetPattern : confs.get("%")) {
                findMatches(fromComponent, targetComponent, fromConfigName, fromConfigName, targetPattern, targets);
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
                    findMatches(fromComponent, targetComponent, fromConfigName, fromConfigName, targetPattern, targets);
                }
            }
        }

        return targets;
    }

    private void findMatches(ComponentResolveMetadata fromComponent, ComponentResolveMetadata targetComponent, String fromConfiguration, String patternConfiguration, String targetPattern, Set<ConfigurationMetadata> targetConfigurations) {
        int startFallback = targetPattern.indexOf('(');
        if (startFallback >= 0) {
            if (targetPattern.endsWith(")")) {
                String preferred = targetPattern.substring(0, startFallback);
                ConfigurationMetadata configuration = targetComponent.getConfiguration(preferred);
                if (configuration != null) {
                    targetConfigurations.add(configuration);
                    return;
                }
                targetPattern = targetPattern.substring(startFallback + 1, targetPattern.length() - 1);
            }
        }

        if (targetPattern.equals("*")) {
            for (String targetName : targetComponent.getConfigurationNames()) {
                ConfigurationMetadata configuration = targetComponent.getConfiguration(targetName);
                if (configuration.isVisible()) {
                    targetConfigurations.add(configuration);
                }
            }
            return;
        }

        if (targetPattern.equals("@")) {
            targetPattern = patternConfiguration;
        } else if (targetPattern.equals("#")) {
            targetPattern = fromConfiguration;
        }

        ConfigurationMetadata configuration = targetComponent.getConfiguration(targetPattern);
        if (configuration == null) {
            throw new ConfigurationNotFoundException(fromComponent.getComponentId(), fromConfiguration, targetPattern, targetComponent.getComponentId());
        }
        targetConfigurations.add(configuration);
    }

    @Override
    public String[] getDependencyConfigurations(final String moduleConfiguration, final String requestedConfiguration) {
        Set<String> mappedConfigs = Sets.newLinkedHashSet();

        Set<String> matchedConfigs = confs.get(moduleConfiguration);
        if (matchedConfigs.isEmpty()) {
            // there is no mapping defined for this configuration, add the 'other' mappings.
            matchedConfigs = confs.get("%");
        }
        mappedConfigs.addAll(matchedConfigs);

        Set<String> wildcardConfigs = confs.get("*");
        mappedConfigs.addAll(wildcardConfigs);

        mappedConfigs = CollectionUtils.collect(mappedConfigs, new Transformer<String, String>() {
            @Override
            public String transform(String original) {
                if (original.startsWith("@")) {
                    return moduleConfiguration + original.substring(1);
                }
                if (original.startsWith("#")) {
                    return requestedConfiguration + original.substring(1);
                }
                return original;
            }
        });

        if (mappedConfigs.remove("*")) {
            StringBuilder r = new StringBuilder("*");
            // merge excluded configurations as one conf like *!A!B
            for (String c : mappedConfigs) {
                if (c.startsWith("!")) {
                    r.append(c);
                }
            }
            return new String[] {r.toString()};
        }
        return mappedConfigs.toArray(new String[mappedConfigs.size()]);
    }

    @Override
    public ModuleExclusion getExclusions(ConfigurationMetadata fromConfiguration) {
        return excludes.isEmpty() ? ModuleExclusions.excludeNone() : ModuleExclusions.excludeAny(getExcludes(fromConfiguration.getHierarchy()));
    }

    private List<Exclude> getExcludes(Collection<String> configurations) {
        List<Exclude> rules = Lists.newArrayList();
        for (Exclude exclude : excludes) {
            Set<String> ruleConfigurations = exclude.getConfigurations();
            if (include(ruleConfigurations, configurations)) {
                rules.add(exclude);
            }
        }
        return rules;
    }

    @Override
    public boolean isChanging() {
        return changing;
    }

    @Override
    public boolean isTransitive() {
        return transitive;
    }

    @Override
    public boolean isForce() {
        return force;
    }

    @Override
    public String getDynamicConstraintVersion() {
        return dynamicConstraintVersion;
    }

    @Override
    public Set<ComponentArtifactMetadata> getArtifacts(ConfigurationMetadata fromConfiguration, ConfigurationMetadata toConfiguration) {
        if (dependencyArtifacts.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> includedConfigurations = fromConfiguration.getHierarchy();
        Set<ComponentArtifactMetadata> artifacts = Sets.newLinkedHashSet();

        for (Artifact depArtifact : dependencyArtifacts) {
            IvyArtifactName ivyArtifactName = depArtifact.getArtifactName();
            Set<String> artifactConfigurations = depArtifact.getConfigurations();
            if (include(artifactConfigurations, includedConfigurations)) {
                ComponentArtifactMetadata artifact = toConfiguration.artifact(ivyArtifactName);
                artifacts.add(artifact);
            }
        }
        return artifacts;
    }

    private boolean include(Iterable<String> configurations, Collection<String> acceptedConfigurations) {
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
    public Set<IvyArtifactName> getArtifacts() {
        return artifacts;
    }

    @Override
    public DependencyMetadata withRequestedVersion(String requestedVersion) {
        if (requestedVersion.equals(requested.getVersion())) {
            return this;
        }
        ModuleVersionSelector newRequested = DefaultModuleVersionSelector.newSelector(requested.getGroup(), requested.getName(), requestedVersion);
        return withRequested(newRequested);
    }

    private DependencyMetadata withRequested(ModuleVersionSelector newRequested) {
        if (newRequested.equals(requested)) {
            return this;
        }
        return new DefaultDependencyMetadata(newRequested, confs, dependencyArtifacts, excludes, dynamicConstraintVersion, changing, transitive);
    }

    @Override
    public DependencyMetadata withTarget(ComponentSelector target) {
        if (target instanceof ModuleComponentSelector) {
            ModuleComponentSelector moduleTarget = (ModuleComponentSelector) target;
            ModuleVersionSelector requestedVersion = DefaultModuleVersionSelector.newSelector(moduleTarget.getGroup(), moduleTarget.getModule(), moduleTarget.getVersion());
            if (requestedVersion.equals(requested)) {
                return this;
            }
            return withRequested(requestedVersion);
        } else if (target instanceof ProjectComponentSelector) {
            // TODO:Prezi what to do here?
            ProjectComponentSelector projectTarget = (ProjectComponentSelector) target;
            return new DefaultProjectDependencyMetadata(projectTarget.getProjectPath(), requested, confs, dependencyArtifacts, excludes, dynamicConstraintVersion, changing, transitive);
        } else {
            throw new AssertionError();
        }
    }

    @Override
    public DependencyMetadata withChanging() {
        if (changing) {
            return this;
        }

        return new DefaultDependencyMetadata(requested, confs, dependencyArtifacts, excludes, dynamicConstraintVersion, true, transitive);
    }

    @Override
    public ComponentSelector getSelector() {
        return DefaultModuleComponentSelector.newSelector(requested.getGroup(), requested.getName(), requested.getVersion());
    }

    public SetMultimap<String, String> getConfMappings() {
        return confs;
    }

    public List<Artifact> getDependencyArtifacts() {
        return dependencyArtifacts;
    }

    public List<Exclude> getDependencyExcludes() {
        return excludes;
    }
}
