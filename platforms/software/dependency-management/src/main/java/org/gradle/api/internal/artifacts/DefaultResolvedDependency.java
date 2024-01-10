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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.CompositeResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ParallelResolveArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.internal.operations.BuildOperationExecutor;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Deprecated
public class DefaultResolvedDependency implements org.gradle.api.artifacts.ResolvedDependency, DependencyGraphNodeResult {
    private final Set<DefaultResolvedDependency> children = new LinkedHashSet<>();
    private final Set<org.gradle.api.artifacts.ResolvedDependency> parents = new LinkedHashSet<>();
    private final ListMultimap<org.gradle.api.artifacts.ResolvedDependency, ResolvedArtifactSet> parentArtifacts = ArrayListMultimap.create();
    private final String name;
    private final ResolvedConfigurationIdentifier resolvedConfigId;
    private final BuildOperationExecutor buildOperationProcessor;
    private final Set<ResolvedArtifactSet> moduleArtifacts;
    private final Map<org.gradle.api.artifacts.ResolvedDependency, Set<org.gradle.api.artifacts.ResolvedArtifact>> allArtifactsCache = new HashMap<>();
    private Set<org.gradle.api.artifacts.ResolvedArtifact> allModuleArtifactsCache;

    public DefaultResolvedDependency(ResolvedConfigurationIdentifier resolvedConfigurationIdentifier, BuildOperationExecutor buildOperationProcessor) {
        this.name = String.format("%s:%s:%s", resolvedConfigurationIdentifier.getModuleGroup(), resolvedConfigurationIdentifier.getModuleName(), resolvedConfigurationIdentifier.getModuleVersion());
        this.resolvedConfigId = resolvedConfigurationIdentifier;
        this.buildOperationProcessor = buildOperationProcessor;
        this.moduleArtifacts = new LinkedHashSet<>();
    }

    @Override
    public org.gradle.api.artifacts.ResolvedDependency getPublicView() {
        return this;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getModuleGroup() {
        return resolvedConfigId.getModuleGroup();
    }

    @Override
    public String getModuleName() {
        return resolvedConfigId.getModuleName();
    }

    @Override
    public String getModuleVersion() {
        return resolvedConfigId.getModuleVersion();
    }

    @Override
    public String getConfiguration() {
        return resolvedConfigId.getConfiguration();
    }

    @Override
    public org.gradle.api.artifacts.ResolvedModuleVersion getModule() {
        return new org.gradle.api.internal.artifacts.ivyservice.modulecache.dynamicversions.DefaultResolvedModuleVersion(resolvedConfigId.getId());
    }

    @Override
    public Set<org.gradle.api.artifacts.ResolvedDependency> getChildren() {
        return ImmutableSet.copyOf(children);
    }

    @Override
    public Collection<? extends DependencyGraphNodeResult> getOutgoingEdges() {
        return children;
    }

    @Override
    public Set<org.gradle.api.artifacts.ResolvedArtifact> getModuleArtifacts() {
        return sort(CompositeResolvedArtifactSet.of(moduleArtifacts));
    }

    @Override
    public Set<org.gradle.api.artifacts.ResolvedArtifact> getAllModuleArtifacts() {
        if (allModuleArtifactsCache == null) {
            Set<org.gradle.api.artifacts.ResolvedArtifact> allArtifacts = new LinkedHashSet<>(getModuleArtifacts());
            for (org.gradle.api.artifacts.ResolvedDependency childResolvedDependency : getChildren()) {
                allArtifacts.addAll(childResolvedDependency.getAllModuleArtifacts());
            }
            allModuleArtifactsCache = allArtifacts;
        }
        return allModuleArtifactsCache;
    }

    @Override
    public Set<org.gradle.api.artifacts.ResolvedArtifact> getParentArtifacts(org.gradle.api.artifacts.ResolvedDependency parent) {
        return sort(getArtifactsForIncomingEdge((DependencyGraphNodeResult) parent));
    }

    private Set<org.gradle.api.artifacts.ResolvedArtifact> sort(ResolvedArtifactSet artifacts) {
        org.gradle.api.internal.artifacts.ivyservice.ArtifactCollectingVisitor visitor =
            new org.gradle.api.internal.artifacts.ivyservice.ArtifactCollectingVisitor(new TreeSet<>(new ResolvedArtifactComparator()));
        ParallelResolveArtifactSet.wrap(artifacts, buildOperationProcessor).visit(visitor);
        return visitor.getArtifacts();
    }

    @Override
    public ResolvedArtifactSet getArtifactsForNode() {
        return CompositeResolvedArtifactSet.of(moduleArtifacts);
    }

    private ResolvedArtifactSet getArtifactsForIncomingEdge(DependencyGraphNodeResult parent) {
        if (!parents.contains(parent)) {
            throw new InvalidUserDataException("Provided dependency (" + parent + ") must be a parent of: " + this);
        }
        return CompositeResolvedArtifactSet.of(parentArtifacts.get((org.gradle.api.artifacts.ResolvedDependency) parent));
    }

    @Override
    public Set<org.gradle.api.artifacts.ResolvedArtifact> getArtifacts(org.gradle.api.artifacts.ResolvedDependency parent) {
        return getParentArtifacts(parent);
    }

    @Override
    public Set<org.gradle.api.artifacts.ResolvedArtifact> getAllArtifacts(org.gradle.api.artifacts.ResolvedDependency parent) {
        if (allArtifactsCache.get(parent) == null) {
            Set<org.gradle.api.artifacts.ResolvedArtifact> allArtifacts = new LinkedHashSet<>(getArtifacts(parent));
            for (org.gradle.api.artifacts.ResolvedDependency childResolvedDependency : getChildren()) {
                for (org.gradle.api.artifacts.ResolvedDependency childParent : childResolvedDependency.getParents()) {
                    allArtifacts.addAll(childResolvedDependency.getAllArtifacts(childParent));
                }
            }
            allArtifactsCache.put(parent, allArtifacts);
        }
        return allArtifactsCache.get(parent);
    }

    @Override
    public Set<org.gradle.api.artifacts.ResolvedDependency> getParents() {
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

    public void addParentSpecificArtifacts(org.gradle.api.artifacts.ResolvedDependency parent, ResolvedArtifactSet artifacts) {
        this.parentArtifacts.put(parent, artifacts);
        moduleArtifacts.add(artifacts);
    }

    public void addModuleArtifacts(ResolvedArtifactSet artifacts) {
        moduleArtifacts.add(artifacts);
    }

    private static class ResolvedArtifactComparator implements Comparator<org.gradle.api.artifacts.ResolvedArtifact> {
        @Override
        public int compare(org.gradle.api.artifacts.ResolvedArtifact artifact1, org.gradle.api.artifacts.ResolvedArtifact artifact2) {
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
            return Integer.compare(artifact1.hashCode(), artifact2.hashCode());
        }
    }
}
