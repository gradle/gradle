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

import java.io.File;

/**
 * A {@link FileCollectionInternal} backed by a publication's artifact set. Implementations expose
 * per-artifact serialization entries so that the configuration cache can capture lazy artifact
 * providers without invoking {@code getFile()} on artifacts whose file has not yet been produced.
 *
 * <p>The configuration-cache codec for file collections uses this marker to skip the default
 * "resolve then iterate files" path (which detonates {@code TransformBackedProvider.beforeRead}
 * for artifacts backed by a mapped task-output provider) in favour of serializing each entry
 * individually.</p>
 */
public interface PublicationArtifactSetFileCollection extends FileCollectionInternal {

    /**
     * Returns pre-normalized entries suitable for configuration-cache serialization.
     *
     * <p>Each element is either:</p>
     * <ul>
     *     <li>a {@link ProviderInternal} for artifacts whose file must be resolved lazily
     *         (e.g. the underlying provider of a {@code LazyPublishArtifact}); or</li>
     *     <li>a {@link File} for artifacts whose file is safe to resolve eagerly at
     *         configuration-cache store time.</li>
     * </ul>
     *
     * @return an iterable of entries in publication order; each element is either a
     *         {@link ProviderInternal} or a {@link File}
     */
    Iterable<Object> getPublicationArtifactSerializationEntries();
}
