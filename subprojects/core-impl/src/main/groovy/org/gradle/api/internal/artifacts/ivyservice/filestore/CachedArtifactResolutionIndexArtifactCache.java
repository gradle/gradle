/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.filestore;

import org.gradle.api.Transformer;
import org.gradle.api.internal.externalresource.CachedExternalResource;
import org.gradle.api.internal.externalresource.CachedExternalResourceIndex;
import org.gradle.api.internal.externalresource.ExternalResourceMetaData;

import java.util.Collections;
import java.util.List;

public class CachedArtifactResolutionIndexArtifactCache extends AbstractArtifactCache<String> {

    public CachedArtifactResolutionIndexArtifactCache(CachedExternalResourceIndex<String> resolutionCache) {
        super(createProducer(resolutionCache));
    }

    private static Transformer<List<CachedArtifact>, String> createProducer(final CachedExternalResourceIndex<String> resolutionCache) {
        return new Transformer<List<CachedArtifact>, String>() {
            public List<CachedArtifact> transform(final String url) {
                CachedExternalResource match = resolutionCache.lookup(url);
                if (match == null) {
                    return Collections.emptyList();
                } else {
                    ExternalResourceMetaData metaData = match.getExternalResourceMetaData();
                    return Collections.singletonList((CachedArtifact) new DefaultCachedArtifact(match.getCachedFile(), metaData.getLastModified()));
                }
            }
        };
    }

}
