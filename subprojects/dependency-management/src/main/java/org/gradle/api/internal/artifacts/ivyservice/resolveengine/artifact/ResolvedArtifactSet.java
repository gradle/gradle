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

import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;

import java.io.File;

/**
 * A container for a set of files or artifacts. May or may not be immutable, and may require building and further resolution.
 */
public interface ResolvedArtifactSet {
    /**
     * Starts preparing the result of this set for later visiting. To visit the final result, call {@link Completion#visit(ArtifactVisitor)} after all work added to the supplied queue has completed.
     *
     * The implementation should notify the provided listener as soon as individual artifacts become available.
     */
    Completion startVisit(BuildOperationQueue<RunnableBuildOperation> actions, AsyncArtifactListener listener);

    /**
     * Collects the build dependencies required to build the artifacts in this set.
     */
    void collectBuildDependencies(BuildDependenciesVisitor visitor);

    Completion EMPTY_RESULT = new Completion() {
        @Override
        public void visit(ArtifactVisitor visitor) {
        }
    };

    ResolvedArtifactSet EMPTY = new ResolvedArtifactSet() {
        @Override
        public Completion startVisit(BuildOperationQueue<RunnableBuildOperation> actions, AsyncArtifactListener listener) {
            return EMPTY_RESULT;
        }

        @Override
        public void collectBuildDependencies(BuildDependenciesVisitor visitor) {
        }
    };

    interface Completion {
        /**
         * Invoked once all async work as completed, to visit the final result. The result is visited using the current thread and in the relevant order.
         * This differs from the notifications passed to {@link AsyncArtifactListener}, which are done from multiple threads and in arbitrary order.
         */
        void visit(ArtifactVisitor visitor);
    }

    /**
     * A listener that is notified as artifacts are made available while visiting the contents of a set. Implementations must be thread safe as they are notified from multiple threads concurrently.
     */
    interface AsyncArtifactListener {
        /**
         * Visits an artifact once it is available. Only called when {@link #requireArtifactFiles()} returns true. Called from any thread and in any order.
         */
        void artifactAvailable(ResolvableArtifact artifact);

        /**
         * Should the file for each artifacts be made available when visiting the result?
         *
         * Returns true here allows the collection to pre-emptively resolve the files in parallel.
         */
        boolean requireArtifactFiles();

        /**
         * Should file dependency artifacts be included in the result?
         */
        boolean includeFileDependencies();

        /**
         * Visits a file. Only called when {@link #includeFileDependencies()} returns true. Should be considered an artifact but is separate as a migration step.
         * Called from any thread and in any order.
         */
        void fileAvailable(File file);

    }
}
