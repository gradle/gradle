/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.memcache

import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactResolveResult
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.BuildableModuleVersionMetaDataResolveResult
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleSource
import org.gradle.api.internal.artifacts.metadata.ModuleVersionArtifactIdentifier
import org.gradle.api.internal.artifacts.metadata.MutableModuleVersionMetaData
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector

class DependencyMetadataCacheTest extends Specification {

    def stats = new DependencyMetadataCacheStats()
    def cache = new DependencyMetadataCache(stats)

    def "caches and supplies remote metadata"() {
        def suppliedMetaData = Stub(MutableModuleVersionMetaData)
        def cachedCopy = Stub(MutableModuleVersionMetaData)
        def originalMetaData = Stub(MutableModuleVersionMetaData)
        def source = Stub(ModuleSource)
        def resolvedResult = Mock(BuildableModuleVersionMetaDataResolveResult.class) {
            getState() >> BuildableModuleVersionMetaDataResolveResult.State.Resolved
            getMetaData() >> originalMetaData
            getModuleSource() >> source
        }
        def result = Mock(BuildableModuleVersionMetaDataResolveResult.class)

        given:
        _ * originalMetaData.copy() >> cachedCopy
        cache.newDependencyResult(newSelector("org", "foo", "1.0"), resolvedResult)

        when:
        def local = cache.supplyLocalMetaData(newSelector("org", "foo", "1.0"), result)
        def differentSelector = cache.supplyMetaData(newSelector("org", "XXX", "1.0"), result)

        then:
        !local
        !differentSelector
        stats.metadataServed == 0
        0 * result._

        when:
        def match = cache.supplyMetaData(newSelector("org", "foo", "1.0"), result)

        then:
        match
        stats.metadataServed == 1
        _ * cachedCopy.copy() >> suppliedMetaData
        1 * result.resolved(suppliedMetaData, source)
    }

    def "caches and supplies remote and local metadata"() {
        def localSource = Stub(ModuleSource)
        def localMetaData = Stub(MutableModuleVersionMetaData)
        _ * localMetaData.copy() >> localMetaData
        def remoteSource = Stub(ModuleSource)
        def remoteMetaData = Stub(MutableModuleVersionMetaData)
        _ * remoteMetaData.copy() >> remoteMetaData
        def resolvedLocal = Mock(BuildableModuleVersionMetaDataResolveResult.class) {
            getMetaData() >> localMetaData
            getModuleSource() >> localSource
            getState() >> BuildableModuleVersionMetaDataResolveResult.State.Resolved
        }
        def resolvedRemote = Mock(BuildableModuleVersionMetaDataResolveResult.class) {
            getMetaData() >> remoteMetaData
            getModuleSource() >> remoteSource
            getState() >> BuildableModuleVersionMetaDataResolveResult.State.Resolved
        }

        cache.newDependencyResult(newSelector("org", "remote", "1.0"), resolvedRemote)
        cache.newLocalDependencyResult(newSelector("org", "local", "1.0"), resolvedLocal)

        def result = Mock(BuildableModuleVersionMetaDataResolveResult.class)

        when:
        def local = cache.supplyLocalMetaData(newSelector("org", "local", "1.0"), result)

        then:
        local
        stats.metadataServed == 1
        1 * result.resolved(localMetaData, localSource)

        when:
        def remote = cache.supplyMetaData(newSelector("org", "remote", "1.0"), result)

        then:
        remote
        stats.metadataServed == 2
        1 * result.resolved(remoteMetaData, remoteSource)
    }

    def "does not cache failed resolves"() {
        def failedResult = Mock(BuildableModuleVersionMetaDataResolveResult.class) { getState() >> BuildableModuleVersionMetaDataResolveResult.State.Failed }
        cache.newDependencyResult(newSelector("org", "lib", "1.0"), failedResult)

        def result = Mock(BuildableModuleVersionMetaDataResolveResult.class)

        when:
        def fromCache = cache.supplyMetaData(newSelector("org", "lib", "1.0"), result)

        then:
        !fromCache
        0 * result._
    }

    def "caches and supplies artifacts"() {
        def fooId = Stub(ModuleVersionArtifactIdentifier)
        def fooFile = new File("foo")
        def fooResult = Mock(BuildableArtifactResolveResult) { getFile() >> fooFile }
        def anotherFooResult = Mock(BuildableArtifactResolveResult)

        def differentId = Stub(ModuleVersionArtifactIdentifier)
        def differentResult = Mock(BuildableArtifactResolveResult)

        cache.newArtifact(fooId, fooResult)

        when:
        def differentCached = cache.supplyArtifact(differentId, differentResult )

        then:
        !differentCached
        0 * differentResult._

        when:
        def fooCached = cache.supplyArtifact(fooId, anotherFooResult )

        then:
        fooCached
        1 * anotherFooResult.resolved(fooFile)
    }
}
