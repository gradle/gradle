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

import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetaData
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector

class InMemoryMetaDataCacheTest extends Specification {

    def stats = new InMemoryCacheStats()
    def cache = new InMemoryMetaDataCache(stats)

    static componentId(String group, String module, String version) {
        return DefaultModuleComponentIdentifier.newId(group, module, version)
    }

    def "caches and supplies module versions"() {
        def versions = ['1', '2', '3'] as Set
        def result = Mock(BuildableModuleVersionListingResolveResult)
        def missingResult = Mock(BuildableModuleVersionListingResolveResult)

        given:
        cache.newModuleVersions(newSelector("org", "foo-remote", "1.0"), Stub(BuildableModuleVersionListingResolveResult) {
            getState() >> BuildableModuleVersionListingResolveResult.State.Listed
            getVersions() >> versions
        })

        when:
        def found = cache.supplyModuleVersions(newSelector("org", "foo-remote", "1.0"), result)
        def missing = cache.supplyModuleVersions(newSelector("org", "foo-local", "1.0"), missingResult)

        then:
        found
        1 * result.listed(versions)

        and:
        !missing
        0 * missingResult._
    }

    def "does not cache failed module version listing"() {
        def failedResult = Stub(BuildableModuleVersionListingResolveResult) {
            getState() >> BuildableModuleVersionListingResolveResult.State.Failed
        }
        cache.newModuleVersions(newSelector("org", "lib", "1.0"), failedResult)

        def result = Mock(BuildableModuleVersionListingResolveResult)

        when:
        def foundInCache = cache.supplyModuleVersions(newSelector("org", "lib", "1.0"), result)

        then:
        !foundInCache
        0 * result._
    }

    def "caches and supplies remote metadata"() {
        def suppliedMetaData = Stub(MutableModuleComponentResolveMetaData)
        def cachedCopy = Stub(MutableModuleComponentResolveMetaData)
        def originalMetaData = Stub(MutableModuleComponentResolveMetaData)
        def resolvedResult = Mock(BuildableModuleComponentMetaDataResolveResult.class) {
            getState() >> BuildableModuleComponentMetaDataResolveResult.State.Resolved
            getMetaData() >> originalMetaData
        }
        def result = Mock(BuildableModuleComponentMetaDataResolveResult.class)

        given:
        _ * originalMetaData.copy() >> cachedCopy
        cache.newDependencyResult(componentId("org", "foo", "1.0"), resolvedResult)

        when:
        def differentSelector = cache.supplyMetaData(componentId("org", "XXX", "1.0"), result)

        then:
        !differentSelector
        stats.metadataServed == 0
        0 * result._

        when:
        def match = cache.supplyMetaData(componentId("org", "foo", "1.0"), result)

        then:
        match
        stats.metadataServed == 1
        _ * cachedCopy.copy() >> suppliedMetaData
        1 * result.resolved(suppliedMetaData)
    }

    def "caches and supplies remote and local metadata"() {
        def moduleMetaData = Stub(MutableModuleComponentResolveMetaData)
        _ * moduleMetaData.copy() >> moduleMetaData
        def resolvedResult = Mock(BuildableModuleComponentMetaDataResolveResult.class) {
            getMetaData() >> moduleMetaData
            getState() >> BuildableModuleComponentMetaDataResolveResult.State.Resolved
        }
        def result = Mock(BuildableModuleComponentMetaDataResolveResult.class)

        given:
        cache.newDependencyResult(componentId("org", "remote", "1.0"), resolvedResult)

        when:
        def found = cache.supplyMetaData(componentId("org", "remote", "1.0"), result)

        then:
        found
        stats.metadataServed == 1
        1 * result.resolved(moduleMetaData)
    }

    def "does not cache failed resolves"() {
        def failedResult = Mock(BuildableModuleComponentMetaDataResolveResult.class) { getState() >> BuildableModuleComponentMetaDataResolveResult.State.Failed }
        cache.newDependencyResult(componentId("org", "lib", "1.0"), failedResult)

        def result = Mock(BuildableModuleComponentMetaDataResolveResult.class)

        when:
        def fromCache = cache.supplyMetaData(componentId("org", "lib", "1.0"), result)

        then:
        !fromCache
        0 * result._
    }
}
