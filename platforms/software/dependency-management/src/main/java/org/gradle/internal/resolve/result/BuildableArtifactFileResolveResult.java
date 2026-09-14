/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.resolve.result;

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.internal.resolve.ArtifactNotFoundException;
import org.gradle.internal.resolve.ArtifactResolveException;
import org.jspecify.annotations.Nullable;

import java.io.File;

public interface BuildableArtifactFileResolveResult extends ResolveResult, BuildableTypedResolveResult<File, ArtifactResolveException>, ResourceAwareResolveResult {

    /**
     * Returns true when the artifact was resolved.
     */
    boolean isSuccessful();

    /**
     * Returns the resolve failure, if any. Returns null when the artifact was resolved or was not found.
     */
    @Override
    @Nullable
    ArtifactResolveException getFailure();

    /**
     * Returns true if the artifact was determined to not exist. This does not represent a
     * failure case. {@link #getFailure()} is null when this method returns true.
     */
    boolean isNotFound();

    /**
     * Creates a failure describing that the artifact was not found, including the locations that were attempted.
     *
     * @throws IllegalStateException when {@link #isNotFound()} is false.
     */
    ArtifactNotFoundException getNotFoundFailure();

    /**
     * @throws ArtifactResolveException If the resolution was unsuccessful, or {@link #getNotFoundFailure()} if the artifact was not found.
     */
    @Override
    File getResult() throws ArtifactResolveException;

    /**
     * Marks the artifact as not found.
     */
    void notFound(ComponentArtifactIdentifier artifact);

}
