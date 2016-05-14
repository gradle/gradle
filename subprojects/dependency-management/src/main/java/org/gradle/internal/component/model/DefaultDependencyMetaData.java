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

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.ivy.core.module.descriptor.ExcludeRule;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector;
import org.gradle.internal.component.external.descriptor.Artifact;
import org.gradle.internal.component.external.descriptor.Dependency;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.local.model.DefaultProjectDependencyMetaData;
import org.gradle.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultDependencyMetaData implements DependencyMetaData {
    private final ModuleVersionSelector requested;
    private final Map<String, List<String>> confs;
    private final Map<IvyArtifactName, Set<String>> dependencyArtifacts;
    private final Map<ExcludeRule, Set<String>> excludeRules;

    private final boolean changing;
    private final boolean transitive;
    private final boolean force;
    private final String dynamicConstraintVersion;

    public DefaultDependencyMetaData(Dependency dependencyState) {
        this.requested = dependencyState.getRequested();
        this.changing = dependencyState.isChanging();
        this.transitive = dependencyState.isTransitive();
        this.force = dependencyState.isForce();
        this.dynamicConstraintVersion = dependencyState.getDynamicConstraintVersion();

        this.confs = Maps.newLinkedHashMap();
        Map<String, List<String>> configMappings = dependencyState.getConfMappings();
        for (String config : configMappings.keySet()) {
            List<String> mappings = new ArrayList<String>(configMappings.get(config));
            confs.put(config, mappings);
        }

        dependencyArtifacts = Maps.newLinkedHashMap();
        for (Artifact dependencyArtifact : dependencyState.getDependencyArtifacts()) {
            dependencyArtifacts.put(dependencyArtifact.getArtifactName(), dependencyArtifact.getConfigurations());
        }

        excludeRules = Maps.newLinkedHashMap();
        for (ExcludeRule excludeRule : dependencyState.getDependencyExcludes()) {
            excludeRules.put(excludeRule, Sets.newHashSet(excludeRule.getConfigurations()));
        }
    }

    public DefaultDependencyMetaData(ModuleVersionIdentifier moduleVersionIdentifier) {
        this(
            new DefaultModuleVersionSelector(moduleVersionIdentifier.getGroup(), moduleVersionIdentifier.getName(), moduleVersionIdentifier.getVersion()),
            Collections.<String, List<String>>emptyMap(),
            Collections.<IvyArtifactName, Set<String>>emptyMap(),
            Collections.<ExcludeRule, Set<String>>emptyMap(),
            moduleVersionIdentifier.getVersion(),
            false,
            true
        );
    }

    public DefaultDependencyMetaData(ModuleComponentIdentifier componentIdentifier) {
        this(
            new DefaultModuleVersionSelector(componentIdentifier.getGroup(), componentIdentifier.getModule(), componentIdentifier.getVersion()),
            Collections.<String, List<String>>emptyMap(),
            Collections.<IvyArtifactName, Set<String>>emptyMap(),
            Collections.<ExcludeRule, Set<String>>emptyMap(),
            componentIdentifier.getVersion(),
            false,
            true
        );
    }

    public DefaultDependencyMetaData(ModuleVersionSelector requested, Map<String, List<String>> confs,
                                     Map<IvyArtifactName, Set<String>> dependencyArtifacts, Map<ExcludeRule, Set<String>> excludeRules,
                                     String dynamicConstraintVersion, boolean changing, boolean transitive) {
        this.requested = requested;
        this.confs = confs;
        this.dependencyArtifacts = dependencyArtifacts;
        this.excludeRules = excludeRules;
        this.changing = changing;
        this.transitive = transitive;
        this.dynamicConstraintVersion = dynamicConstraintVersion;
        this.force = false;
    }

    public Map<String, List<String>> getConfigMappings() {
        return confs;
    }

    public Map<IvyArtifactName, Set<String>> getArtifactMappings() {
        return dependencyArtifacts;
    }

    public Set<ExcludeRule> getAllExcludeRules() {
        return excludeRules.keySet();
    }

    @Override
    public String toString() {
        return "dependency: " + requested;
    }

    public ModuleVersionSelector getRequested() {
        return requested;
    }

    @Override
    public String[] getModuleConfigurations() {
        return confs.keySet().toArray(new String[confs.keySet().size()]);
    }

    @Override
    public String[] getDependencyConfigurations(final String moduleConfiguration, final String requestedConfiguration) {
        Set<String> mappedConfigs = Sets.newLinkedHashSet();

        List<String> matchedConfigs = confs.get(moduleConfiguration);
        if (matchedConfigs == null) {
            // there is no mapping defined for this configuration, add the 'other' mappings.
            matchedConfigs = confs.get("%");
        }
        if (matchedConfigs != null) {
            mappedConfigs.addAll(matchedConfigs);
        }

        List<String> wildcardConfigs = confs.get("*");
        if (wildcardConfigs != null) {
            mappedConfigs.addAll(wildcardConfigs);
        }

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

    public ExcludeRule[] getExcludeRules(Collection<String> configurations) {
        Set<ExcludeRule> rules = Sets.newLinkedHashSet();
        for (Map.Entry<ExcludeRule, Set<String>> entry : excludeRules.entrySet()) {
            ExcludeRule excludeRule = entry.getKey();
            Set<String> ruleConfigurations = entry.getValue();
            if (include(ruleConfigurations, configurations)) {
                rules.add(excludeRule);
            }
        }
        return rules.toArray(new ExcludeRule[rules.size()]);
    }

    public boolean isChanging() {
        return changing;
    }

    public boolean isTransitive() {
        return transitive;
    }

    public boolean isForce() {
        return force;
    }

    public String getDynamicConstraintVersion() {
        return dynamicConstraintVersion;
    }

    public Set<ComponentArtifactMetaData> getArtifacts(ConfigurationMetaData fromConfiguration, ConfigurationMetaData toConfiguration) {
        Set<String> includedConfigurations = fromConfiguration.getHierarchy();
        Set<ComponentArtifactMetaData> artifacts = Sets.newLinkedHashSet();

        for (Map.Entry<IvyArtifactName, Set<String>> entry : dependencyArtifacts.entrySet()) {
            IvyArtifactName ivyArtifactName = entry.getKey();
            Set<String> artifactConfigurations = entry.getValue();
            if (include(artifactConfigurations, includedConfigurations)) {
                ComponentArtifactMetaData artifact = toConfiguration.artifact(ivyArtifactName);
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

    public Set<IvyArtifactName> getArtifacts() {
        return dependencyArtifacts.keySet();
    }

    public DependencyMetaData withRequestedVersion(String requestedVersion) {
        if (requestedVersion.equals(requested.getVersion())) {
            return this;
        }
        ModuleVersionSelector newRequested = DefaultModuleVersionSelector.newSelector(requested.getGroup(), requested.getName(), requestedVersion);
        return withRequested(newRequested);
    }

    private DependencyMetaData withRequested(ModuleVersionSelector newRequested) {
        if (newRequested.equals(requested)) {
            return this;
        }
        return new DefaultDependencyMetaData(newRequested, confs, dependencyArtifacts, excludeRules, dynamicConstraintVersion, changing, transitive);
    }

    @Override
    public DependencyMetaData withTarget(ComponentSelector target) {
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
            return new DefaultProjectDependencyMetaData(projectTarget.getProjectPath(), requested, confs, dependencyArtifacts, excludeRules, dynamicConstraintVersion, changing, transitive);
        } else {
            throw new AssertionError();
        }
    }

    public DependencyMetaData withChanging() {
        if (changing) {
            return this;
        }

        return new DefaultDependencyMetaData(requested, confs, dependencyArtifacts, excludeRules, dynamicConstraintVersion, true, transitive);
    }

    public ComponentSelector getSelector() {
        return DefaultModuleComponentSelector.newSelector(requested.getGroup(), requested.getName(), requested.getVersion());
    }
}
