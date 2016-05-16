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

package org.gradle.internal.component.external.descriptor;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.apache.ivy.core.module.descriptor.DependencyArtifactDescriptor;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.ExcludeRule;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.util.CollectionUtils;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class Dependency {
    private static final Field DEPENDENCY_CONFIG_FIELD;
    static {
        try {
            DEPENDENCY_CONFIG_FIELD = DefaultDependencyDescriptor.class.getDeclaredField("confs");
            DEPENDENCY_CONFIG_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private final ModuleVersionSelector requested;
    private final String dynamicConstraintVersion;

    private final boolean force;
    private final boolean changing;
    private final boolean transitive;

    private final Map<String, List<String>> confMappings = Maps.newLinkedHashMap();
    private final List<Artifact> dependencyArtifacts = Lists.newArrayList();
    private final List<ExcludeRule> dependencyExcludes = Lists.newArrayList();


    public Dependency(ModuleVersionSelector requested, String dynamicConstraintVersion, boolean force, boolean changing, boolean transitive) {
        this.requested = requested;
        this.dynamicConstraintVersion = dynamicConstraintVersion;
        this.force = force;
        this.changing = changing;
        this.transitive = transitive;
    }

    public void addArtifact(IvyArtifactName newArtifact, Collection<String> configurations) {
        Artifact artifact = new Artifact(newArtifact, CollectionUtils.toSet(configurations));
        dependencyArtifacts.add(artifact);
    }

    public void addDependencyConfiguration(String from, String to) {
        addConfMapping(from, to);
    }

    public void addDependencyConfiguration(String from, List<String> to) {
        confMappings.put(from, to);
    }

    void addConfMapping(String from, String to) {
        List<String> mappings = confMappings.get(from);
        if (mappings == null) {
            mappings = Lists.newArrayList();
            confMappings.put(from, mappings);
        }
        if (!mappings.contains(to)) {
            mappings.add(to);
        }
    }

    public void addExcludeRule(ExcludeRule rule) {
        dependencyExcludes.add(rule);
    }

    public ModuleVersionSelector getRequested() {
        return requested;
    }

    public boolean isForce() {
        return force;
    }

    public boolean isChanging() {
        return changing;
    }

    public boolean isTransitive() {
        return transitive;
    }

    public Map<String, List<String>> getConfMappings() {
        return confMappings;
    }

    public List<Artifact> getDependencyArtifacts() {
        return dependencyArtifacts;
    }

    public List<ExcludeRule> getDependencyExcludes() {
        return dependencyExcludes;
    }

    public String getDynamicConstraintVersion() {
        return dynamicConstraintVersion;
    }

    public static Dependency forDependencyDescriptor(DependencyDescriptor dependencyDescriptor) {
        Dependency dep = new Dependency(
            DefaultModuleVersionSelector.newSelector(dependencyDescriptor.getDependencyRevisionId()),
            dependencyDescriptor.getDynamicConstraintDependencyRevisionId().getRevision(),
            false,
            dependencyDescriptor.isChanging(),
            dependencyDescriptor.isTransitive());

        Map<String, List<String>> configMappings = readConfigMappings(dependencyDescriptor);
        for (String from : configMappings.keySet()) {
            for (String to : configMappings.get(from)) {
                dep.addConfMapping(from, to);
            }
        }

        for (DependencyArtifactDescriptor dependencyArtifactDescriptor : dependencyDescriptor.getAllDependencyArtifacts()) {
            IvyArtifactName ivyArtifactName = DefaultIvyArtifactName.forIvyArtifact(dependencyArtifactDescriptor);
            dep.addArtifact(ivyArtifactName, Sets.newHashSet(dependencyArtifactDescriptor.getConfigurations()));
        }

        dep.dependencyExcludes.addAll(DefaultExcludeRule.forIvyExcludes(dependencyDescriptor.getAllExcludeRules()));
        return dep;
    }

    // TODO:DAZ Get rid of this reflection (will need to hook directly into the parser)
    private static Map<String, List<String>> readConfigMappings(DependencyDescriptor dependencyDescriptor) {
        if (dependencyDescriptor instanceof DefaultDependencyDescriptor) {
            try {
                return (Map<String, List<String>>) DEPENDENCY_CONFIG_FIELD.get(dependencyDescriptor);
            } catch (IllegalAccessException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }

        String[] modConfs = dependencyDescriptor.getModuleConfigurations();
        Map<String, List<String>> results = Maps.newLinkedHashMap();
        for (String modConf : modConfs) {
            results.put(modConf, Arrays.asList(dependencyDescriptor.getDependencyConfigurations(modConfs)));
        }
        return results;
    }

}
