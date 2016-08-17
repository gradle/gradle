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
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.util.CollectionUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public abstract class Dependency {
    private final ModuleVersionSelector requested;
    private final Map<String, List<String>> confMappings = Maps.newLinkedHashMap();
    private final List<Artifact> dependencyArtifacts = Lists.newArrayList();
    private final List<Exclude> dependencyExcludes = Lists.newArrayList();

    public Dependency(ModuleVersionSelector requested) {
        this.requested = requested;
    }

    public void addArtifact(IvyArtifactName newArtifact, Collection<String> configurations) {
        Artifact artifact = new Artifact(newArtifact, CollectionUtils.toSet(configurations));
        dependencyArtifacts.add(artifact);
    }

    public void addDependencyConfiguration(String from, String to) {
        List<String> mappings = confMappings.get(from);
        if (mappings == null) {
            mappings = Lists.newArrayList();
            confMappings.put(from, mappings);
        }
        if (!mappings.contains(to)) {
            mappings.add(to);
        }
    }

    public void addDependencyConfiguration(String from, List<String> to) {
        confMappings.put(from, to);
    }

    public void addExcludeRule(Exclude rule) {
        dependencyExcludes.add(rule);
    }

    public ModuleVersionSelector getRequested() {
        return requested;
    }

    public abstract boolean isForce();

    public abstract boolean isChanging();

    public abstract boolean isTransitive();

    public Map<String, List<String>> getConfMappings() {
        return confMappings;
    }

    public List<Artifact> getDependencyArtifacts() {
        return dependencyArtifacts;
    }

    public List<Exclude> getDependencyExcludes() {
        return dependencyExcludes;
    }

    public abstract String getDynamicConstraintVersion();
}
