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

import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.tasks.TaskDependency;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * A container for a set of files or artifacts. May or may not be immutable, and may require building and further resolution.
 */
public interface ResolvedArtifactSet {
    /**
     * Returns the resolved artifacts in this set, if any.
     */
    Set<ResolvedArtifact> getArtifacts();

    /**
     * Collects the build dependencies required to build the artifacts in this set.
     */
    void collectBuildDependencies(Collection<? super TaskDependency> dest);

    /**
     * Visits the contents of this set.
     */
    void visit(ArtifactVisitor visitor);

    ResolvedArtifactSet EMPTY = new ResolvedArtifactSet() {
        @Override
        public Set<ResolvedArtifact> getArtifacts() {
            return Collections.emptySet();
        }

        @Override
        public void collectBuildDependencies(Collection<? super TaskDependency> dest) {
        }

        @Override
        public void visit(ArtifactVisitor visitor) {
        }
    };
}
