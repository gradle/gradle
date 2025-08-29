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

import org.gradle.internal.component.model.VariantIdentifier;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.external.model.ImmutableCapabilities;

/**
 * A visitor over the contents of a {@link ResolvedArtifactSet}. A {@link ResolvedArtifactSet} may contain zero or more sets of files, each set containing zero or more artifacts.
 */
public interface ArtifactVisitor {
    /**
     * Called prior to scheduling resolution of a set of artifacts. Should be called in result order.
     */
    default FileCollectionStructureVisitor.VisitType prepareForVisit(FileCollectionInternal.Source source) {
        return FileCollectionStructureVisitor.VisitType.Visit;
    }

    /**
     * Visits an artifact from the artifact set.
     * <p>
     * Artifacts are resolved but not necessarily available unless {@link #requireArtifactFiles()} returns true.
     * A given artifact may be visited multiple times. The implementation is required to filter out duplicates.
     *
     * @param artifactSetName The name of the artifact set that this artifact belongs to.
     * @param sourceVariantId The identifier of the node in the graph that produced this artifact.
     * @param attributes The attributes of the artifact.
     * @param capabilities The capabilities of the artifact.
     * @param artifact The artifact.
     */
    void visitArtifact(DisplayName artifactSetName, VariantIdentifier sourceVariantId, ImmutableAttributes attributes, ImmutableCapabilities capabilities, ResolvableArtifact artifact);

    /**
     * Should the file for each artifact be made available prior to calling {@link #visitArtifact(DisplayName, VariantIdentifier, ImmutableAttributes, ImmutableCapabilities, ResolvableArtifact)}?
     *
     * Returns true here allows the collection to preemptively resolve the files in parallel.
     */
    boolean requireArtifactFiles();

    /**
     * Called when some problem occurs visiting some element of the set. Visiting may continue.
     */
    void visitFailure(Throwable failure);

    /**
     * Called after a set of artifacts has been visited.
     */
    default void endVisitCollection(FileCollectionInternal.Source source) {
    }
}
