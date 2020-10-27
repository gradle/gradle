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
     * Visits the contents of the set, adding any remaining work to finalise the set of artifacts to the given queue.
     */
    void visit(BuildOperationQueue<RunnableBuildOperation> actions, Visitor visitor);

    /**
     * Visits the local artifacts of this set, if known without further resolution. Ignores artifacts that are not build locally and local artifacts that cannot be determined without further resolution.
     */
    void visitLocalArtifacts(LocalArtifactVisitor visitor);

    /**
     * Visits the external artifacts of this set.
     */
    void visitExternalArtifacts(Action<ResolvableArtifact> visitor);

    ResolvedArtifactSet EMPTY = new ResolvedArtifactSet() {
        @Override
        public void visit(BuildOperationQueue<RunnableBuildOperation> actions, Visitor visitor) {
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

    interface Artifacts {
        /**
         * Invoked once all async work as completed, to visit the final result. The result is visited using the current thread and in the relevant order.
         */
        void visit(ArtifactVisitor visitor);
    }

    /**
     * A listener that is notified as artifacts are made available while visiting the contents of a set. Implementations must be thread safe as they are notified from multiple threads concurrently.
     */
    interface Visitor {
        /**
         * Called prior to scheduling resolution of a set of the given type. Should be called in result order.
         */
        FileCollectionStructureVisitor.VisitType prepareForVisit(FileCollectionInternal.Source source);

        /**
         * Visits zero or more artifacts.
         */
        void visitArtifacts(Artifacts artifacts);

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
