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

import org.gradle.api.artifacts.ArtifactIdentifier
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactResolveResult
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.BuildableModuleVersionMetaDataResolveResult
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleVersionMetaData
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier.newId
import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector

/**
 * By Szczepan Faber on 4/19/13
 */
class DependencyMetadataCacheTest extends Specification {

    def stats = new DependencyMetadataCacheStats()
    def cache = new DependencyMetadataCache(stats)

    def "caches and supplies remote metadata"() {
        def resolvedResult = Mock(BuildableModuleVersionMetaDataResolveResult.class) {
            getState() >> BuildableModuleVersionMetaDataResolveResult.State.Resolved
            getMetaData() >> Stub(ModuleVersionMetaData)
        }
        cache.newDependencyResult(newSelector("org", "foo", "1.0"), resolvedResult)
        def result = Mock(BuildableModuleVersionMetaDataResolveResult.class)

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
        1 * result.resolved(_, _, _, _)
    }

    def "caches and supplies remote and local metadata"() {
        def resolvedLocal = Mock(BuildableModuleVersionMetaDataResolveResult.class) {
            getMetaData() >> Mock(ModuleVersionMetaData)
            getState() >> BuildableModuleVersionMetaDataResolveResult.State.Resolved
        }
        def resolvedRemote = Mock(BuildableModuleVersionMetaDataResolveResult.class) {
            getMetaData() >> Mock(ModuleVersionMetaData)
            getState() >> BuildableModuleVersionMetaDataResolveResult.State.Resolved
        }

        cache.newDependencyResult(newSelector("org", "remote", "1.0"), resolvedRemote)
        cache.newLocalDependencyResult(newSelector("org", "local", "1.0"), resolvedLocal)

        def result = Mock(BuildableModuleVersionMetaDataResolveResult.class)

        when:
        def local = cache.supplyLocalMetaData(newSelector("org", "local", "1.0"), result)
        def remote = cache.supplyMetaData(newSelector("org", "remote", "1.0"), result)

        then:
        local
        remote
        stats.metadataServed == 2
        1 * result.resolved(_, _, _, _)
        1 * result.resolved(_, _, _, _)
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
        def foo = Stub(ArtifactIdentifier) { getModuleVersionIdentifier() >> newId("org", "foo", "1.0") }
        def fooFile = new File("foo")
        def fooResult = Mock(BuildableArtifactResolveResult) { getFile() >> fooFile }
        def anotherFooResult = Mock(BuildableArtifactResolveResult)

        def different = Stub(ArtifactIdentifier) { getModuleVersionIdentifier() >> newId("org", "XXX", "1.0") }
        def differentResult = Mock(BuildableArtifactResolveResult)

        cache.newArtifact(foo, fooResult)

        when:
        def differentCached = cache.supplyArtifact(different, differentResult )

        then:
        !differentCached
        0 * differentResult._

        when:
        def fooCached = cache.supplyArtifact(foo, anotherFooResult )

        then:
        fooCached
        1 * anotherFooResult.resolved(fooFile)
    }
}
