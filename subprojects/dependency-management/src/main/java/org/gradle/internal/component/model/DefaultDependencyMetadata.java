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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
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
import org.gradle.internal.component.local.model.DefaultProjectDependencyMetadata;
import org.gradle.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultDependencyMetadata implements DependencyMetadata {
    private final ModuleVersionSelector requested;
    private final Map<String, List<String>> confs;
    private final Map<IvyArtifactName, Set<String>> dependencyArtifacts;
    private final Map<Exclude, Set<String>> excludes;

    private final boolean changing;
    private final boolean transitive;
    private final boolean force;
    private final String dynamicConstraintVersion;

    public DefaultDependencyMetadata(Dependency dependencyState) {
        this.requested = dependencyState.getRequested();
        this.changing = dependencyState.isChanging();
        this.transitive = dependencyState.isTransitive();
        this.force = dependencyState.isForce();
        this.dynamicConstraintVersion = dependencyState.getDynamicConstraintVersion();

        Map<String, List<String>> configMappings = dependencyState.getConfMappings();
        Set<String> configs = configMappings.keySet();
        // LinkedHashMap here because order matters
        this.confs = configs.isEmpty() ? Collections.<String, List<String>>emptyMap() : new LinkedHashMap<String, List<String>>(configs.size());
        for (String config : configs) {
            List<String> mappings = new ArrayList<String>(configMappings.get(config));
            confs.put(config, mappings);
        }

        List<Artifact> artifacts = dependencyState.getDependencyArtifacts();
        dependencyArtifacts = artifacts.isEmpty() ? Collections.<IvyArtifactName, Set<String>>emptyMap() : new HashMap<IvyArtifactName, Set<String>>(artifacts.size());
        for (Artifact dependencyArtifact : artifacts) {
            this.dependencyArtifacts.put(dependencyArtifact.getArtifactName(), dependencyArtifact.getConfigurations());
        }

        List<Exclude> dependencyExcludes = dependencyState.getDependencyExcludes();
        excludes = dependencyExcludes.isEmpty() ? Collections.<Exclude, Set<String>>emptyMap() : new HashMap<Exclude, Set<String>>(dependencyExcludes.size());
        for (Exclude exclude : dependencyExcludes) {
            this.excludes.put(exclude, Sets.newHashSet(exclude.getConfigurations()));
        }
    }

    public DefaultDependencyMetadata(ModuleVersionIdentifier moduleVersionIdentifier) {
        this(
            new DefaultModuleVersionSelector(moduleVersionIdentifier.getGroup(), moduleVersionIdentifier.getName(), moduleVersionIdentifier.getVersion()),
            Collections.<String, List<String>>emptyMap(),
            Collections.<IvyArtifactName, Set<String>>emptyMap(),
            Collections.<Exclude, Set<String>>emptyMap(),
            moduleVersionIdentifier.getVersion(),
            false,
            true
        );
    }

    public DefaultDependencyMetadata(ModuleComponentIdentifier componentIdentifier) {
        this(
            new DefaultModuleVersionSelector(componentIdentifier.getGroup(), componentIdentifier.getModule(), componentIdentifier.getVersion()),
            Collections.<String, List<String>>emptyMap(),
            Collections.<IvyArtifactName, Set<String>>emptyMap(),
            Collections.<Exclude, Set<String>>emptyMap(),
            componentIdentifier.getVersion(),
            false,
            true
        );
    }

    public DefaultDependencyMetadata(ModuleVersionSelector requested, Map<String, List<String>> confs,
                                     Map<IvyArtifactName, Set<String>> dependencyArtifacts, Map<Exclude, Set<String>> excludes,
                                     String dynamicConstraintVersion, boolean changing, boolean transitive) {
        this.requested = requested;
        this.confs = confs;
        this.dependencyArtifacts = dependencyArtifacts;
        this.excludes = excludes;
        this.changing = changing;
        this.transitive = transitive;
        this.dynamicConstraintVersion = dynamicConstraintVersion;
        this.force = false;
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

    public List<Exclude> getExcludes(Collection<String> configurations) {
        List<Exclude> rules = Lists.newArrayList();
        for (Map.Entry<Exclude, Set<String>> entry : excludes.entrySet()) {
            Exclude exclude = entry.getKey();
            Set<String> ruleConfigurations = entry.getValue();
            if (include(ruleConfigurations, configurations)) {
                rules.add(exclude);
            }
        }
        return rules;
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

    public Set<ComponentArtifactMetadata> getArtifacts(ConfigurationMetadata fromConfiguration, ConfigurationMetadata toConfiguration) {
        Set<String> includedConfigurations = fromConfiguration.getHierarchy();
        Set<ComponentArtifactMetadata> artifacts = Sets.newLinkedHashSet();

        for (Map.Entry<IvyArtifactName, Set<String>> entry : dependencyArtifacts.entrySet()) {
            IvyArtifactName ivyArtifactName = entry.getKey();
            Set<String> artifactConfigurations = entry.getValue();
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

    public Set<IvyArtifactName> getArtifacts() {
        return dependencyArtifacts.keySet();
    }

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

    public DependencyMetadata withChanging() {
        if (changing) {
            return this;
        }

        return new DefaultDependencyMetadata(requested, confs, dependencyArtifacts, excludes, dynamicConstraintVersion, true, transitive);
    }

    public ComponentSelector getSelector() {
        return DefaultModuleComponentSelector.newSelector(requested.getGroup(), requested.getName(), requested.getVersion());
    }
}
