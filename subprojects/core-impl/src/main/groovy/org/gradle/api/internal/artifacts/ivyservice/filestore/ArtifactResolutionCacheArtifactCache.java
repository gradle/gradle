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
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.api.internal.artifacts.ivyservice.artifactcache.ArtifactResolutionCache;
import org.gradle.api.internal.artifacts.ivyservice.artifactcache.CachedArtifactResolution;
import org.gradle.internal.Factory;

import java.util.Collections;
import java.util.List;

public class ArtifactResolutionCacheArtifactCache extends AbstractArtifactCache<String> {

    public ArtifactResolutionCacheArtifactCache(ArtifactResolutionCache resolutionCache, CacheLockingManager cacheLockingManager) {
        super(createProducer(resolutionCache, cacheLockingManager));
    }

    private static Transformer<List<CachedArtifact>, String> createProducer(final ArtifactResolutionCache resolutionCache, final CacheLockingManager cacheLockingManager) {
        return new Transformer<List<CachedArtifact>, String>() {
            public List<CachedArtifact> transform(final String url) {

                return cacheLockingManager.useCache(
                        String.format("find cached artifacts for url %s", url),
                        new Factory<List<CachedArtifact>>() {

                            public List<CachedArtifact> create() {
                                CachedArtifactResolution match = resolutionCache.getCachedArtifactResolution(url);
                                if (match == null) {
                                    return Collections.emptyList();
                                } else {
                                    return Collections.singletonList((CachedArtifact) new DefaultCachedArtifact(match.getArtifactFile(), match.getArtifactLastModified()));
                                }
                            }

                        }
                );

            }
        };
    }

}
