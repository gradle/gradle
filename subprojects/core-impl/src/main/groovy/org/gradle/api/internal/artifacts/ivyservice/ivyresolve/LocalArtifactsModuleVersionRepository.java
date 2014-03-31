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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import org.gradle.api.internal.artifacts.ivyservice.ArtifactResolveContext;
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactSetResolveResult;
import org.gradle.api.internal.artifacts.metadata.ComponentMetaData;

// TODO This is a bit of a hack to allow us to avoid caching on the filesystem when the set of artifacts can be calculated cheaply.
// A better long term solution might be to push the caching down to the resource layer, so that the answer to artifactExists() could be cached.
public interface LocalArtifactsModuleVersionRepository extends ModuleVersionRepository {
    /**
     * Resolves a set of artifacts belonging to the gi  ven module, without making any network requests. Any failures are packaged up in the result.
     * If no local resolution is possible, then the result should be set via {@link #resolveModuleArtifacts}.
     */
    void localResolveModuleArtifacts(ComponentMetaData component, ArtifactResolveContext context, BuildableArtifactSetResolveResult result);
}
