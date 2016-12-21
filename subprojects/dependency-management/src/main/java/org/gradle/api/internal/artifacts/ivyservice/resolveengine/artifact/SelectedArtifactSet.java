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

import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;

import java.io.File;
import java.util.Collection;

/**
 * A container of artifacts that match some criteria. Not every query method is available, depending on which details are available.
 */
public interface SelectedArtifactSet {
    /**
     * Collects the build dependencies required to build the artifacts in this result.
     */
    <T extends Collection<Object>> T collectBuildDependencies(T dest);

    /**
     * Collects files into the given collection. Fails when any file cannot be resolved.
     *
     * @return the collection
     * @throws ResolveException On any failure.
     */
    <T extends Collection<? super File>> T collectFiles(T dest) throws ResolveException;

    /**
     * Collects all artifacts into the given collection. Fails when any artifact cannot be resolved.
     *
     * @return the collection
     * @throws ResolveException On any failure.
     */
    <T extends Collection<? super ResolvedArtifactResult>> T collectArtifacts(T dest) throws ResolveException;

    /**
     * Visits the files and artifacts of this set.
     */
    void visitArtifacts(ArtifactVisitor visitor);
}
