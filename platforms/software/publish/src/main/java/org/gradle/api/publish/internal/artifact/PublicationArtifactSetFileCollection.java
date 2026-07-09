/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.publish.internal.artifact;

import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.provider.ProviderInternal;

/**
 * A {@link FileCollectionInternal} backed by a publication's artifact set. Implementations expose
 * per-artifact serialization entries so that the configuration cache can capture lazy artifact
 * providers without invoking {@code getFile()} on artifacts whose file has not yet been produced.
 * <p>
 * The configuration-cache codec for file collections uses this marker to skip the default
 * "resolve then iterate files" path (which calls {@code TransformBackedProvider.beforeRead}
 * for artifacts backed by a mapped task-output provider, even if the task has <strong>NOT</strong>
 * yet been executed) in favour of serializing each entry individually.
 */
public interface PublicationArtifactSetFileCollection extends FileCollectionInternal {

    /**
     * Returns per-artifact providers suitable for configuration-cache serialization, in
     * publication order.
     *
     * <p>For lazy artifacts (e.g. those backed by a {@code LazyPublishArtifact}) the returned
     * element is the artifact's underlying provider, which the codec captures without resolving
     * — deferring evaluation to task execution time when the producing task has run.</p>
     *
     * <p>For eager artifacts (those whose file is already known at configuration time) the
     * returned element is a fixed-value provider wrapping the file. This gives a uniform
     * {@link ProviderInternal} contract at the cost of a two-byte provider envelope per eager
     * entry in the CC state — a negligible overhead that pays for a simpler codec branch and
     * a stronger interface signature.</p>
     *
     * @return an iterable of {@link ProviderInternal} entries in publication order
     */
    Iterable<ProviderInternal<?>> getPublicationArtifactSerializationEntries();
}
