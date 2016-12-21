/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.artifacts;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import org.apache.commons.lang.ObjectUtils;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.ResolvedModuleVersion;
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.DefaultResolvedModuleVersion;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.CompositeArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class DefaultResolvedDependency implements ResolvedDependency, DependencyGraphNodeResult {
    private final Set<DefaultResolvedDependency> children = new LinkedHashSet<DefaultResolvedDependency>();
    private final Set<ResolvedDependency> parents = new LinkedHashSet<ResolvedDependency>();
    private final ListMultimap<ResolvedDependency, ResolvedArtifactSet> parentArtifacts = ArrayListMultimap.create();
    private final Long id;
    private final String name;
    private final ResolvedConfigurationIdentifier resolvedConfigId;
    private final Set<ResolvedArtifactSet> moduleArtifacts;
    private final Map<ResolvedDependency, Set<ResolvedArtifact>> allArtifactsCache = new HashMap<ResolvedDependency, Set<ResolvedArtifact>>();
    private Set<ResolvedArtifact> allModuleArtifactsCache;

    public DefaultResolvedDependency(Long id, ResolvedConfigurationIdentifier resolvedConfigurationIdentifier) {
        this.id = id;
        this.name = String.format("%s:%s:%s", resolvedConfigurationIdentifier.getModuleGroup(), resolvedConfigurationIdentifier.getModuleName(), resolvedConfigurationIdentifier.getModuleVersion());
        this.resolvedConfigId = resolvedConfigurationIdentifier;
        this.moduleArtifacts = new LinkedHashSet<ResolvedArtifactSet>();
    }

    @Override
    public ResolvedDependency getPublicView() {
        return this;
    }

    public String getName() {
        return name;
    }

    @Override
    public Long getNodeId() {
        return id;
    }

    public String getModuleGroup() {
        return resolvedConfigId.getModuleGroup();
    }

    public String getModuleName() {
        return resolvedConfigId.getModuleName();
    }

    public String getModuleVersion() {
        return resolvedConfigId.getModuleVersion();
    }

    public String getConfiguration() {
        return resolvedConfigId.getConfiguration();
    }

    public ResolvedModuleVersion getModule() {
        return new DefaultResolvedModuleVersion(resolvedConfigId.getId());
    }

    public Set<ResolvedDependency> getChildren() {
        return ImmutableSet.<ResolvedDependency>copyOf(children);
    }

    @Override
    public Collection<? extends DependencyGraphNodeResult> getOutgoingEdges() {
        return children;
    }

    public Set<ResolvedArtifact> getModuleArtifacts() {
        return sort(CompositeArtifactSet.of(moduleArtifacts));
    }

    public Set<ResolvedArtifact> getAllModuleArtifacts() {
        if (allModuleArtifactsCache == null) {
            Set<ResolvedArtifact> allArtifacts = new LinkedHashSet<ResolvedArtifact>();
            allArtifacts.addAll(getModuleArtifacts());
            for (ResolvedDependency childResolvedDependency : getChildren()) {
                allArtifacts.addAll(childResolvedDependency.getAllModuleArtifacts());
            }
            allModuleArtifactsCache = allArtifacts;
        }
        return allModuleArtifactsCache;
    }

    public Set<ResolvedArtifact> getParentArtifacts(ResolvedDependency parent) {
        return sort(getArtifactsForIncomingEdge((DependencyGraphNodeResult) parent));
    }

    private Set<ResolvedArtifact> sort(ResolvedArtifactSet artifacts) {
        Set<ResolvedArtifact> result = new TreeSet<ResolvedArtifact>(new ResolvedArtifactComparator());
        result.addAll(artifacts.getArtifacts());
        return result;
    }

    @Override
    public ResolvedArtifactSet getArtifactsForIncomingEdge(DependencyGraphNodeResult parent) {
        if (!parents.contains(parent)) {
            throw new InvalidUserDataException("Provided dependency (" + parent + ") must be a parent of: " + this);
        }
        return CompositeArtifactSet.of(parentArtifacts.get((ResolvedDependency) parent));
    }

    public Set<ResolvedArtifact> getArtifacts(ResolvedDependency parent) {
        return getParentArtifacts(parent);
    }

    public Set<ResolvedArtifact> getAllArtifacts(ResolvedDependency parent) {
        if (allArtifactsCache.get(parent) == null) {
            Set<ResolvedArtifact> allArtifacts = new LinkedHashSet<ResolvedArtifact>();
            allArtifacts.addAll(getArtifacts(parent));
            for (ResolvedDependency childResolvedDependency : getChildren()) {
                for (ResolvedDependency childParent : childResolvedDependency.getParents()) {
                    allArtifacts.addAll(childResolvedDependency.getAllArtifacts(childParent));
                }
            }
            allArtifactsCache.put(parent, allArtifacts);
        }
        return allArtifactsCache.get(parent);
    }

    public Set<ResolvedDependency> getParents() {
        return parents;
    }

    public String toString() {
        return name + ";" + getConfiguration();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultResolvedDependency that = (DefaultResolvedDependency) o;
        return resolvedConfigId.equals(that.resolvedConfigId);
    }

    @Override
    public int hashCode() {
        return resolvedConfigId.hashCode();
    }

    public void addChild(DefaultResolvedDependency child) {
        children.add(child);
        child.parents.add(this);
    }

    public void addParentSpecificArtifacts(ResolvedDependency parent, ResolvedArtifactSet artifacts) {
        this.parentArtifacts.put(parent, artifacts);
        moduleArtifacts.add(artifacts);
    }

    private static class ResolvedArtifactComparator implements Comparator<ResolvedArtifact> {
        public int compare(ResolvedArtifact artifact1, ResolvedArtifact artifact2) {
            int diff = artifact1.getName().compareTo(artifact2.getName());
            if (diff != 0) {
                return diff;
            }
            diff = ObjectUtils.compare(artifact1.getClassifier(), artifact2.getClassifier());
            if (diff != 0) {
                return diff;
            }
            diff = ObjectUtils.compare(artifact1.getExtension(), artifact2.getExtension());
            if (diff != 0) {
                return diff;
            }
            diff = artifact1.getType().compareTo(artifact2.getType());
            if (diff != 0) {
                return diff;
            }
            // Use an arbitrary ordering when the artifacts have the same public attributes
            return artifact1.hashCode() - artifact2.hashCode();
        }
    }
}
