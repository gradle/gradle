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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve

import org.apache.ivy.core.module.descriptor.Artifact
import org.apache.ivy.core.module.id.ArtifactRevisionId
import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy
import org.gradle.api.internal.artifacts.ivyservice.artifactcache.ArtifactResolutionCache
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.ModuleResolutionCache
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleDescriptorCache
import spock.lang.Specification
import spock.lang.Unroll

class CachingModuleVersionRepositoryTest extends Specification {

    ModuleVersionRepository realRepo = Mock()
    ModuleResolutionCache moduleResolutionCache = Mock()
    ModuleDescriptorCache moduleDescriptorCache = Mock()
    ArtifactResolutionCache artifactResolutionCache = Mock()
    CachePolicy cachePolicy = Mock()

    CachingModuleVersionRepository repo = new CachingModuleVersionRepository(realRepo, moduleResolutionCache, moduleDescriptorCache, artifactResolutionCache, cachePolicy)
    
    @Unroll "last modified date is cached - lastModified = #lastModified"(Date lastModified) {
        given:
        DownloadedArtifact downloadedArtifact = new DownloadedArtifact(new File("artifact"), lastModified, "remote url")
        Artifact artifact = Mock()
        ArtifactRevisionId id = Mock()
        _ * realRepo.isLocal() >> false
        _ * artifactResolutionCache.getCachedArtifactResolution(_, _) >> null
        _ * realRepo.download(artifact) >> downloadedArtifact
        _ * artifact.getId() >> id

        when:
        repo.download(artifact)
        
        then:
        1 * artifactResolutionCache.storeArtifactFile(realRepo, id, downloadedArtifact.localFile, downloadedArtifact.lastModified, downloadedArtifact.source)
        
        where:
        lastModified << [new Date(), null]
    }

}
