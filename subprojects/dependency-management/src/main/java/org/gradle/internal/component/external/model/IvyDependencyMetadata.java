/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.component.external.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusion;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions;
import org.gradle.internal.component.external.descriptor.Artifact;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.ConfigurationNotFoundException;
import org.gradle.internal.component.model.DefaultDependencyMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.Exclude;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class IvyDependencyMetadata extends DefaultDependencyMetadata {
    private final String dynamicConstraintVersion;
    private final boolean force;
    private final boolean changing;
    private final boolean transitive;
    private final SetMultimap<String, String> confs;
    private final List<Exclude> excludes;

    public IvyDependencyMetadata(ModuleVersionSelector requested, String dynamicConstraintVersion, boolean force, boolean changing, boolean transitive, Multimap<String, String> confMappings, List<Artifact> artifacts, List<Exclude> excludes) {
        super(requested, artifacts);
        this.dynamicConstraintVersion = dynamicConstraintVersion;
        this.force = force;
        this.changing = changing;
        this.transitive = transitive;
        this.confs = ImmutableSetMultimap.copyOf(confMappings);
        this.excludes = ImmutableList.copyOf(excludes);
    }

    public IvyDependencyMetadata(ModuleVersionSelector requested, ListMultimap<String, String> confMappings) {
        this(requested, requested.getVersion(), false, false, true, confMappings, Collections.<Artifact>emptyList(), Collections.<Exclude>emptyList());
    }

    @Override
    public String toString() {
        return "dependency: " + getRequested() + ", confs: " + confs;
    }

    @Override
    protected DependencyMetadata withRequested(ModuleVersionSelector newRequested) {
        return new IvyDependencyMetadata(newRequested, dynamicConstraintVersion, force, changing, transitive, confs, getDependencyArtifacts(), excludes);
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
    public Set<String> getModuleConfigurations() {
        return confs.keySet();
    }

    public SetMultimap<String, String> getConfMappings() {
        return confs;
    }

    @Override
    public Set<ConfigurationMetadata> selectConfigurations(ComponentResolveMetadata fromComponent, ConfigurationMetadata fromConfiguration, ComponentResolveMetadata targetComponent, AttributesSchema attributesSchema) {
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

    public List<Exclude> getDependencyExcludes() {
        return excludes;
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

}
