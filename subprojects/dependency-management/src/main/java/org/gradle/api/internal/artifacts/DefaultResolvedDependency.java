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
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCollectingVisitor;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.dynamicversions.DefaultResolvedModuleVersion;
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

public class DefaultResolvedDependency implements ResolvedDependency, DependencyGraphNodeResult {
    private final Set<DefaultResolvedDependency> children = new LinkedHashSet<>();
    private final Set<ResolvedDependency> parents = new LinkedHashSet<>();
    private final ListMultimap<ResolvedDependency, ResolvedArtifactSet> parentArtifacts = ArrayListMultimap.create();
    private final String name;
    private final ResolvedConfigurationIdentifier resolvedConfigId;
    private final BuildOperationExecutor buildOperationProcessor;
    private final Set<ResolvedArtifactSet> moduleArtifacts;
    private final Map<ResolvedDependency, Set<ResolvedArtifact>> allArtifactsCache = new HashMap<>();
    private Set<ResolvedArtifact> allModuleArtifactsCache;

    public DefaultResolvedDependency(ResolvedConfigurationIdentifier resolvedConfigurationIdentifier, BuildOperationExecutor buildOperationProcessor) {
        this.name = String.format("%s:%s:%s", resolvedConfigurationIdentifier.getModuleGroup(), resolvedConfigurationIdentifier.getModuleName(), resolvedConfigurationIdentifier.getModuleVersion());
        this.resolvedConfigId = resolvedConfigurationIdentifier;
        this.buildOperationProcessor = buildOperationProcessor;
        this.moduleArtifacts = new LinkedHashSet<>();
    }

    @Override
    public ResolvedDependency getPublicView() {
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
    public ResolvedModuleVersion getModule() {
        return new DefaultResolvedModuleVersion(resolvedConfigId.getId());
    }

    @Override
    public Set<ResolvedDependency> getChildren() {
        return ImmutableSet.copyOf(children);
    }

    @Override
    public Collection<? extends DependencyGraphNodeResult> getOutgoingEdges() {
        return children;
    }

    @Override
    public Set<ResolvedArtifact> getModuleArtifacts() {
        return sort(CompositeResolvedArtifactSet.of(moduleArtifacts));
    }

    @Override
    public Set<ResolvedArtifact> getAllModuleArtifacts() {
        if (allModuleArtifactsCache == null) {
            Set<ResolvedArtifact> allArtifacts = new LinkedHashSet<>(getModuleArtifacts());
            for (ResolvedDependency childResolvedDependency : getChildren()) {
                allArtifacts.addAll(childResolvedDependency.getAllModuleArtifacts());
            }
            allModuleArtifactsCache = allArtifacts;
        }
        return allModuleArtifactsCache;
    }

    @Override
    public Set<ResolvedArtifact> getParentArtifacts(ResolvedDependency parent) {
        return sort(getArtifactsForIncomingEdge((DependencyGraphNodeResult) parent));
    }

    private Set<ResolvedArtifact> sort(ResolvedArtifactSet artifacts) {
        ArtifactCollectingVisitor visitor = new ArtifactCollectingVisitor(new TreeSet<>(new ResolvedArtifactComparator()));
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
        return CompositeResolvedArtifactSet.of(parentArtifacts.get((ResolvedDependency) parent));
    }

    @Override
    public Set<ResolvedArtifact> getArtifacts(ResolvedDependency parent) {
        return getParentArtifacts(parent);
    }

    @Override
    public Set<ResolvedArtifact> getAllArtifacts(ResolvedDependency parent) {
        if (allArtifactsCache.get(parent) == null) {
            Set<ResolvedArtifact> allArtifacts = new LinkedHashSet<>(getArtifacts(parent));
            for (ResolvedDependency childResolvedDependency : getChildren()) {
                for (ResolvedDependency childParent : childResolvedDependency.getParents()) {
                    allArtifacts.addAll(childResolvedDependency.getAllArtifacts(childParent));
                }
            }
            allArtifactsCache.put(parent, allArtifacts);
        }
        return allArtifactsCache.get(parent);
    }

    @Override
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

    public void addModuleArtifacts(ResolvedArtifactSet artifacts) {
        moduleArtifacts.add(artifacts);
    }

    private static class ResolvedArtifactComparator implements Comparator<ResolvedArtifact> {
        @Override
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
