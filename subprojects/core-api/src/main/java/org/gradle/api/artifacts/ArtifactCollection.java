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

package org.gradle.api.artifacts;

import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;

import java.util.Collection;
import java.util.Set;

/**
 * A collection of artifacts resolved for a configuration. The configuration is resolved on demand when
 * the collection is queried.
 *
 * @since 3.4
 */
public interface ArtifactCollection extends Iterable<ResolvedArtifactResult> {
    /**
     * A file collection containing the files for all artifacts in this collection.
     * This is primarily useful to wire this artifact collection as a task input.
     */
    FileCollection getArtifactFiles();

    /**
     * Returns the resolved artifacts, performing the resolution if required.
     * This will resolve the artifact metadata and download the artifact files as required.
     *
     * @throws ResolveException On failure to resolve or download any artifact.
     */
    Set<ResolvedArtifactResult> getArtifacts();

    /**
     * Returns the resolved artifacts as a {@link Provider} of {@link ResolvedArtifactResult} instances.
     * The returned {@link Provider} is live, and tracks the producer tasks of this artifact collection.
     * The provider will resolve the artifact metadata and download the artifact files as required.
     *
     * @since 7.4
     */
    Provider<Set<ResolvedArtifactResult>> getResolvedArtifacts();

    /**
     * Returns any failures to resolve the artifacts for this collection.
     *
     * @since 4.0
     *
     * @return A collection of exceptions, one for each failure in resolution.
     */
    Collection<Throwable> getFailures();
}
