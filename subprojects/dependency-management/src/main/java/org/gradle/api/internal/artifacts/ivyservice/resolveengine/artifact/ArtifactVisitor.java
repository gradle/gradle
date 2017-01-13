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

import org.gradle.api.Nullable;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentIdentifier;

import java.io.File;

/**
 * A visitor over the contents of a {@link ResolvedArtifactSet}.
 */
public interface ArtifactVisitor {
    /**
     * Visits an artifact. Artifacts are resolved but not necessarily downloaded.
     */
    void visitArtifact(ResolvedArtifact artifact);

    /**
     * Should {@link #visitFiles(ComponentIdentifier, Iterable)} be called?
     */
    boolean includeFiles();

    /**
     * Visits a file collection. Should be considered a set of artifacts but is separate as a migration step.
     */
    void visitFiles(@Nullable ComponentIdentifier componentIdentifier, Iterable<File> files);
}
