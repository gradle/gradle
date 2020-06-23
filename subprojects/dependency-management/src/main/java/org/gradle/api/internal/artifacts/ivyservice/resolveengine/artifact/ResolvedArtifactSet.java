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

import org.gradle.api.Action;
import org.gradle.api.internal.artifacts.transform.TransformationSubject;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;

import java.io.File;

/**
 * A container for a set of files or artifacts. May or may not be immutable, and may require building and further resolution.
 */
public interface ResolvedArtifactSet extends TaskDependencyContainer {
    /**
     * Starts preparing the result of this set for later visiting. To visit the final result, call {@link Completion#visit(ArtifactVisitor)} after all work added to the supplied queue has completed.
     *
     * The implementation should notify the provided listener as soon as individual artifacts become available.
     */
    Completion startVisit(BuildOperationQueue<RunnableBuildOperation> actions, AsyncArtifactListener listener);

    /**
     * Visits the local artifacts of this set, if known without further resolution. Ignores artifacts that are not build locally and local artifacts that cannot be determined without further resolution.
     */
    void visitLocalArtifacts(LocalArtifactVisitor visitor);

    /**
     * Visits the external artifacts of this set.
     */
    void visitExternalArtifacts(Action<ResolvableArtifact> visitor);

    Completion EMPTY_RESULT = visitor -> {
    };

    ResolvedArtifactSet EMPTY = new ResolvedArtifactSet() {
        @Override
        public Completion startVisit(BuildOperationQueue<RunnableBuildOperation> actions, AsyncArtifactListener listener) {
            return EMPTY_RESULT;
        }

        @Override
        public void visitLocalArtifacts(LocalArtifactVisitor visitor) {
        }

        @Override
        public void visitExternalArtifacts(Action<ResolvableArtifact> visitor) {
        }

        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
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
         * Called prior to scheduling resolution of a set of the given type. Should be called in result order.
         */
        FileCollectionStructureVisitor.VisitType prepareForVisit(FileCollectionInternal.Source source);

        /**
         * Visits an artifact once its file is available. Only called when {@link #requireArtifactFiles()} returns true. Called from any thread and in any order.
         */
        void artifactAvailable(ResolvableArtifact artifact);

        /**
         * Should the file for each artifacts be made available when visiting the result?
         *
         * Returns true here allows the collection to preemptively resolve the files in parallel.
         */
        boolean requireArtifactFiles();
    }

    interface LocalArtifactSet {
        Object getId();

        String getDisplayName();

        TaskDependencyContainer getTaskDependencies();

        TransformationSubject calculateSubject();

        ResolvableArtifact transformedTo(File output);
    }

    interface LocalArtifactVisitor {
        void visitArtifact(LocalArtifactSet artifactSet);
    }
}
