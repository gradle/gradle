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

package org.gradle.api.internal.artifacts.resolutioncache

import org.apache.ivy.core.module.descriptor.Artifact
import org.apache.ivy.core.module.descriptor.DependencyDescriptor
import org.apache.ivy.core.module.id.ArtifactId
import org.apache.ivy.core.module.id.ArtifactRevisionId
import org.apache.ivy.core.module.id.ModuleId
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.gradle.CacheUsage
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheMetaData
import org.gradle.api.internal.artifacts.ivyservice.DefaultCacheLockingManager
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleVersionDescriptor
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleVersionRepository
import org.gradle.cache.internal.CacheFactory
import org.gradle.cache.internal.DefaultCacheRepository
import org.gradle.testfixtures.internal.InMemoryCacheFactory
import org.gradle.util.TemporaryFolder
import org.gradle.util.TimeProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Unroll
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.DownloadedArtifact

class DefaultArtifactResolutionCacheTest extends Specification {

    @Rule TemporaryFolder tmp = new TemporaryFolder()

    Date time = new Date(0)
    TimeProvider timeProvider = new TimeProvider() {
        long getCurrentTime() { time.getTime() }
    }

    CacheFactory cacheFactory = new InMemoryCacheFactory()

    ArtifactCacheMetaData cacheMetaData
    DefaultCacheRepository cacheRepository
    DefaultCacheLockingManager cacheLockingManager
    DefaultArtifactResolutionCache cache

    def setup() {
        cacheMetaData = new ArtifactCacheMetaData() {
            File getCacheDir() {
                tmp.createDir()
            }
        }

        cacheRepository = new DefaultCacheRepository(tmp.createDir('user-home'), tmp.createDir('project-cache'), CacheUsage.ON, cacheFactory)
        cacheLockingManager = new DefaultCacheLockingManager(cacheRepository)
        cache = new DefaultArtifactResolutionCache(
                cacheMetaData, timeProvider, cacheLockingManager
        )
    }

    @Unroll "stores entry - lastModified = #lastModified, artifactUrl = #artifactUrl"() {
        given:
        def repo = repo()
        def arid = arid()
        def artifactFile = tmp.createFile("artifact") << "content"
        
        when:
        cache.storeArtifactFile(repo, arid, artifactFile, lastModified, artifactUrl)
        
        then:
        def resolution = cache.getCachedArtifactResolution(repo, arid)
        
        and:
        resolution != null
        resolution.artifactLastModified == lastModified
        resolution.artifactUrl == artifactUrl
        resolution.artifactFile == artifactFile
        
        when:
        def noUrl = artifactUrl == null
        def byUrlResolution = noUrl ? null : cache.getCachedArtifactResolution(artifactUrl)

        then:
        noUrl == (byUrlResolution == null)
        noUrl || byUrlResolution.artifactLastModified == resolution.artifactLastModified
        noUrl || byUrlResolution.artifactFile == resolution.artifactFile
        noUrl || byUrlResolution.artifactUrl == resolution.artifactUrl


        where:
        lastModified | artifactUrl
        new Date()   | null
        null         | "abc"
        null         | null
        new Date()   | "abc"
    }

    def "storing with a different repo but same url overwrites the previous"() {
        given:
        def repo1 = repo("repo1")
        def repo2 = repo("repo2")

        def date1 = new Date()
        def date2 = date1 + 1

        def url = "remote-artifact"
        File artifactFile = tmp.createFile("artifact") << "content"

        when:
        cache.storeArtifactFile(repo1, arid(name: "foo"), artifactFile, date1, url)
        cache.storeArtifactFile(repo2, arid(name:  "bar"), artifactFile, date2, url)
        
        then:
        def cached = cache.getCachedArtifactResolution(url)

        and:
        cached.artifactLastModified == date2
    }

    ModuleVersionRepository repo(id = "repo") {
        new ModuleVersionRepository() {
            String getId() {
                id            
            }

            String getName() {
                id
            }

            ModuleVersionDescriptor getDependency(DependencyDescriptor dd) {
                throw new UnsupportedOperationException("getDependency")
            }

            DownloadedArtifact download(Artifact artifact) {
                throw new UnsupportedOperationException("download")
            }

            boolean isLocal() {
                throw new UnsupportedOperationException("isLocal")
            }
        }
    }
    
    ArtifactRevisionId arid(Map attrs = [:]) {
        Map defaults = [
                org: "org", name: "name", revision: "1.0",
                type: "type", ext: "ext"
        ]
    
       attrs = defaults + attrs
        
        ModuleId mid = new ModuleId(attrs.org, attrs.name)
        new ArtifactRevisionId(
                new ArtifactId(mid, mid.name, attrs.type, attrs.ext),
                new ModuleRevisionId(mid, attrs.revision)
        ) 
    }
}
