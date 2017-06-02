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

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.attributes.AttributeContainer;

import java.io.File;

/**
 * A visitor over the contents of a {@link ResolvedArtifactSet}.
 */
public interface ArtifactVisitor {
    /**
     * Visits an artifact. Artifacts are resolved but not necessarily downloaded unless {@link #requireArtifactFiles()} returns true.
     */
    void visitArtifact(AttributeContainer variant, ResolvableArtifact artifact);

    /**
     * Should the file for each artifacts be made available prior to calling {@link #visitArtifact(AttributeContainer, ResolvableArtifact)}?
     *
     * Returns true here allows the collection to pre-emptively resolve the files in parallel.
     */
    boolean requireArtifactFiles();

    /**
     * Should {@link #visitFile(ComponentArtifactIdentifier, AttributeContainer, File)} be called?
     */
    boolean includeFiles();

    /**
     * Visits a file. Should be considered an artifact but is separate as a migration step.
     */
    void visitFile(ComponentArtifactIdentifier artifactIdentifier, AttributeContainer variant, File file);

    /**
     * Called when some problem occurs visiting some element of the set. Visiting may continue.
     */
    void visitFailure(Throwable failure);
}
