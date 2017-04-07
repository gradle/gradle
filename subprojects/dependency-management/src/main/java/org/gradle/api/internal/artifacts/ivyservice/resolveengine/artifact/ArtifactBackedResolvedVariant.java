/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import org.gradle.api.Buildable;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.DescribableBuildOperation;
import org.gradle.internal.operations.RunnableBuildOperation;

import java.util.Collection;
import java.util.Map;

class ArtifactBackedResolvedVariant implements ResolvedVariant, ArtifactFailuresCollector {
    private final AttributeContainerInternal attributes;
    private final ImmutableSet<ResolvedArtifact> artifacts;
    private volatile Map<ResolvedArtifact, Throwable> failures;

    private ArtifactBackedResolvedVariant(AttributeContainerInternal attributes, Collection<? extends ResolvedArtifact> artifacts) {
        this.attributes = attributes;
        this.artifacts = ImmutableSet.copyOf(artifacts);
    }

    public static ResolvedVariant create(AttributeContainerInternal attributes, Collection<? extends ResolvedArtifact> artifacts) {
        if (artifacts.isEmpty()) {
            return new EmptyResolvedVariant(attributes);
        }
        if (artifacts.size() == 1) {
            return new SingleArtifactResolvedVariant(attributes, artifacts.iterator().next());
        }
        return new ArtifactBackedResolvedVariant(attributes, artifacts);
    }

    @Override
    public void addPrepareActions(BuildOperationQueue<RunnableBuildOperation> actions, final ArtifactVisitor visitor) {
        if (visitor.shouldPerformPreemptiveDownload()) {
            for (ResolvedArtifact artifact : artifacts) {
                if (!isFromIncludedBuild(artifact)) {
                    actions.add(new DownloadArtifactFile(artifact, this));
                }
            }
        }
    }

    @Override
    public void visit(ArtifactVisitor visitor) {
        for (ResolvedArtifact artifact : artifacts) {
            if (hasFailure(artifact)) {
                visitor.visitFailure(getFailure(artifact));
            } else {
                visitor.visitArtifact(attributes, artifact);
            }
        }
    }

    @Override
    public void collectBuildDependencies(Collection<? super TaskDependency> dest) {
        for (ResolvedArtifact artifact : artifacts) {
            dest.add(((Buildable) artifact).getBuildDependencies());
        }
    }

    @Override
    public AttributeContainerInternal getAttributes() {
        return attributes;
    }

    @Override
    // May be accessed concurrently, by download workers
    public synchronized void addFailure(ResolvedArtifact resolvedArtifact, Throwable err) {
        if (failures == null) {
            failures = Maps.newHashMap();
        }
        failures.put(resolvedArtifact, err);
    }

    // always accessed from the same thread, doesn't need synchronization
    private boolean hasFailure(ResolvedArtifact artifact) {
        return failures != null && failures.containsKey(artifact);
    }

    // always accessed from the same thread, doesn't need synchronization
    private Throwable getFailure(ResolvedArtifact artifact) {
        // no null-check here since it's illegal to call this without checking hasFailure first
        return failures.get(artifact);
    }

    private static boolean isFromIncludedBuild(ResolvedArtifact artifact) {
        ComponentIdentifier id = artifact.getId().getComponentIdentifier();
        return id instanceof ProjectComponentIdentifier
            && !((ProjectComponentIdentifier) id).getBuild().isCurrentBuild();
    }

    private static class SingleArtifactResolvedVariant implements ResolvedVariant, ArtifactFailuresCollector {
        private final AttributeContainerInternal variantAttributes;
        private final ResolvedArtifact artifact;
        private volatile Throwable failure;

        SingleArtifactResolvedVariant(AttributeContainerInternal variantAttributes, ResolvedArtifact artifact) {
            this.variantAttributes = variantAttributes;
            this.artifact = artifact;
        }

        public AttributeContainerInternal getAttributes() {
            return variantAttributes;
        }

        @Override
        public void addPrepareActions(BuildOperationQueue<RunnableBuildOperation> actions, final ArtifactVisitor visitor) {
            if (visitor.shouldPerformPreemptiveDownload() && !isFromIncludedBuild(artifact))  {
                actions.add(new DownloadArtifactFile(artifact, this));
            }
        }

        @Override
        public void visit(ArtifactVisitor visitor) {
            if (failure != null) {
                visitor.visitFailure(failure);
            } else {
                visitor.visitArtifact(variantAttributes, artifact);
            }
        }

        @Override
        public void collectBuildDependencies(Collection<? super TaskDependency> dest) {
            dest.add(((Buildable) artifact).getBuildDependencies());
        }

        @Override
        public void addFailure(ResolvedArtifact resolvedArtifact, Throwable err) {
            failure = err;
        }
    }

    private static class DownloadArtifactFile implements RunnableBuildOperation, DescribableBuildOperation<ComponentArtifactIdentifier> {
        private final ResolvedArtifact artifact;
        private final ArtifactFailuresCollector artifactFailures;

        DownloadArtifactFile(ResolvedArtifact artifact, ArtifactFailuresCollector artifactFailures) {
            this.artifact = artifact;
            this.artifactFailures = artifactFailures;
        }

        @Override
        public void run() {
            try {
                artifact.getFile();
            } catch (Throwable t) {
                artifactFailures.addFailure(artifact, t);
            }
        }

        @Override
        public String getDescription() {
            return "Resolve artifact " + artifact;
        }

        @Override
        public ComponentArtifactIdentifier getOperationDescriptor() {
            return artifact.getId();
        }

        @Override
        public String getProgressDisplayName() {
            return null;
        }
    }

}
