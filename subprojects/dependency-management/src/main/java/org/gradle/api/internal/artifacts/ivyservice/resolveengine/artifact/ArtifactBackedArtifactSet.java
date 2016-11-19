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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.Buildable;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.tasks.TaskDependency;

import java.util.Collection;
import java.util.Set;

public class ArtifactBackedArtifactSet implements ResolvedArtifactSet {
    private final ImmutableSet<ResolvedArtifact> artifacts;

    private ArtifactBackedArtifactSet(Collection<? extends ResolvedArtifact> artifacts) {
        this.artifacts = ImmutableSet.copyOf(artifacts);
    }

    public static ResolvedArtifactSet of(Collection<? extends ResolvedArtifact> artifacts) {
        if (artifacts.isEmpty()) {
            return EMPTY;
        }
        if (artifacts.size() == 1) {
            return new SingletonSet(artifacts.iterator().next());
        }
        return new ArtifactBackedArtifactSet(artifacts);
    }

    @Override
    public Set<ResolvedArtifact> getArtifacts() {
        return artifacts;
    }

    @Override
    public void collectBuildDependencies(Collection<? super TaskDependency> dest) {
        for (ResolvedArtifact artifact : artifacts) {
            dest.add(((Buildable) artifact).getBuildDependencies());
        }
    }

    @Override
    public void visit(ArtifactVisitor visitor) {
        for (ResolvedArtifact artifact : artifacts) {
            visitor.visitArtifact(artifact);
        }
    }

    private static class SingletonSet implements ResolvedArtifactSet {
        private final ResolvedArtifact artifact;

        SingletonSet(ResolvedArtifact artifact) {
            this.artifact = artifact;
        }

        @Override
        public Set<ResolvedArtifact> getArtifacts() {
            return ImmutableSet.of(artifact);
        }

        @Override
        public void visit(ArtifactVisitor visitor) {
            visitor.visitArtifact(artifact);
        }

        @Override
        public void collectBuildDependencies(Collection<? super TaskDependency> dest) {
            dest.add(((Buildable) artifact).getBuildDependencies());
        }
    }
}
