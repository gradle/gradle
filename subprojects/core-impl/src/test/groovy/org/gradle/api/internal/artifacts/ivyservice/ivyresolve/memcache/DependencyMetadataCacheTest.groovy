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

import org.gradle.api.internal.artifacts.component.DefaultModuleComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactResolveResult
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ArtifactResolveException
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.BuildableModuleVersionMetaDataResolveResult
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.BuildableModuleVersionSelectionResolveResult
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleSource
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleVersionListing
import org.gradle.api.internal.artifacts.metadata.ModuleVersionArtifactIdentifier
import org.gradle.api.internal.artifacts.metadata.MutableModuleVersionMetaData
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector

class DependencyMetadataCacheTest extends Specification {

    def stats = new DependencyMetadataCacheStats()
    def cache = new DependencyMetadataCache(stats)

    static componentId(String group, String module, String version) {
        return DefaultModuleComponentIdentifier.newId(group, module, version)
    }

    def "caches and supplies local and remote module versions"() {
        def remoteListing = Mock(ModuleVersionListing)
        def localListing = Mock(ModuleVersionListing)
        def result = Mock(BuildableModuleVersionSelectionResolveResult)
        def missingResult = Mock(BuildableModuleVersionSelectionResolveResult)

        given:
        cache.newModuleVersions(newSelector("org", "foo-remote", "1.0"), Stub(BuildableModuleVersionSelectionResolveResult) {
            getState() >> BuildableModuleVersionSelectionResolveResult.State.Listed
            getVersions() >> remoteListing
        })
        cache.newLocalModuleVersions(newSelector("org", "foo-local", "1.0"), Stub(BuildableModuleVersionSelectionResolveResult) {
            getState() >> BuildableModuleVersionSelectionResolveResult.State.Listed
            getVersions() >> localListing
        })

        when:
        def local = cache.supplyLocalModuleVersions(newSelector("org", "foo-local", "1.0"), result)
        def missingLocal = cache.supplyLocalModuleVersions(newSelector("org", "foo-remote", "1.0"), missingResult)

        then:
        local
        1 * result.listed(localListing)

        and:
        !missingLocal
        0 * missingResult._

        when:
        def remote = cache.supplyModuleVersions(newSelector("org", "foo-remote", "1.0"), result)
        def missingRemote = cache.supplyModuleVersions(newSelector("org", "foo-local", "1.0"), missingResult)

        then:
        remote
        1 * result.listed(remoteListing)

        and:
        !missingRemote
        0 * missingResult._
    }

    def "does not cache failed module version listing"() {
        def failedResult = Stub(BuildableModuleVersionSelectionResolveResult) {
            getState() >> BuildableModuleVersionSelectionResolveResult.State.Failed
        }
        cache.newModuleVersions(newSelector("org", "lib", "1.0"), failedResult)
        cache.newLocalModuleVersions(newSelector("org", "lib", "1.0"), failedResult)

        def result = Mock(BuildableModuleVersionSelectionResolveResult)

        when:
        def remoteFromCache = cache.supplyModuleVersions(newSelector("org", "lib", "1.0"), result)
        def localFromCache = cache.supplyLocalModuleVersions(newSelector("org", "lib", "1.0"), result)

        then:
        !remoteFromCache
        !localFromCache
        0 * result._
    }

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
        cache.newDependencyResult(componentId("org", "foo", "1.0"), resolvedResult)

        when:
        def local = cache.supplyLocalMetaData(componentId("org", "foo", "1.0"), result)
        def differentSelector = cache.supplyMetaData(componentId("org", "XXX", "1.0"), result)

        then:
        !local
        !differentSelector
        stats.metadataServed == 0
        0 * result._

        when:
        def match = cache.supplyMetaData(componentId("org", "foo", "1.0"), result)

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

        cache.newDependencyResult(componentId("org", "remote", "1.0"), resolvedRemote)
        cache.newLocalDependencyResult(componentId("org", "local", "1.0"), resolvedLocal)

        def result = Mock(BuildableModuleVersionMetaDataResolveResult.class)

        when:
        def local = cache.supplyLocalMetaData(componentId("org", "local", "1.0"), result)

        then:
        local
        stats.metadataServed == 1
        1 * result.resolved(localMetaData, localSource)

        when:
        def remote = cache.supplyMetaData(componentId("org", "remote", "1.0"), result)

        then:
        remote
        stats.metadataServed == 2
        1 * result.resolved(remoteMetaData, remoteSource)
    }

    def "does not cache failed resolves"() {
        def failedResult = Mock(BuildableModuleVersionMetaDataResolveResult.class) { getState() >> BuildableModuleVersionMetaDataResolveResult.State.Failed }
        cache.newDependencyResult(componentId("org", "lib", "1.0"), failedResult)

        def result = Mock(BuildableModuleVersionMetaDataResolveResult.class)

        when:
        def fromCache = cache.supplyMetaData(componentId("org", "lib", "1.0"), result)

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

    def "does not cache failed artifact resolves"() {
        def artifactId = Stub(ModuleVersionArtifactIdentifier)
        def failedResult = Stub(BuildableArtifactResolveResult) { getFailure() >> new ArtifactResolveException("bad") }
        cache.newArtifact(artifactId, failedResult)

        def result = Mock(BuildableArtifactResolveResult)

        when:
        def fromCache = cache.supplyArtifact(artifactId, result)

        then:
        !fromCache
        0 * result._
    }
}
