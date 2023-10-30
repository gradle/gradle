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

import org.gradle.api.internal.tasks.TaskDependencyContainer;

/**
 * A container of artifacts that match some criteria.
 */
public interface SelectedArtifactSet extends TaskDependencyContainer {
    /**
     * Visits the artifacts of this set. Does not include any artifacts that could not be selected. Failures to select or resolve artifacts are supplied to the visitor.
     */
    void visitArtifacts(ArtifactVisitor visitor, boolean continueOnSelectionFailure);

    /**
     * Visits the files of this set. Does not include any files that could not be selected. Failures to select or resolve artifacts are supplied to the visitor.
     */
    default void visitFiles(ResolvedFileVisitor visitor, boolean continueOnSelectionFailure) {
        visitArtifacts(new ArtifactVisitorToResolvedFileVisitorAdapter(visitor), continueOnSelectionFailure);
    }
}
