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
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.BuildableModuleVersionMetaDataResolveResult
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.BuildableModuleVersionSelectionResolveResult
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleSource
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleVersionListing
import org.gradle.api.internal.artifacts.metadata.MutableModuleVersionMetaData
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector

class InMemoryMetaDataCacheTest extends Specification {

    def stats = new InMemoryCacheStats()
    def cache = new InMemoryMetaDataCache(stats)

    static componentId(String group, String module, String version) {
        return DefaultModuleComponentIdentifier.newId(group, module, version)
    }

    def "caches and supplies module versions"() {
        def listing = Mock(ModuleVersionListing)
        def result = Mock(BuildableModuleVersionSelectionResolveResult)
        def missingResult = Mock(BuildableModuleVersionSelectionResolveResult)

        given:
        cache.newModuleVersions(newSelector("org", "foo-remote", "1.0"), Stub(BuildableModuleVersionSelectionResolveResult) {
            getState() >> BuildableModuleVersionSelectionResolveResult.State.Listed
            getVersions() >> listing
        })

        when:
        def found = cache.supplyModuleVersions(newSelector("org", "foo-remote", "1.0"), result)
        def missing = cache.supplyModuleVersions(newSelector("org", "foo-local", "1.0"), missingResult)

        then:
        found
        1 * result.listed(listing)

        and:
        !missing
        0 * missingResult._
    }

    def "does not cache failed module version listing"() {
        def failedResult = Stub(BuildableModuleVersionSelectionResolveResult) {
            getState() >> BuildableModuleVersionSelectionResolveResult.State.Failed
        }
        cache.newModuleVersions(newSelector("org", "lib", "1.0"), failedResult)

        def result = Mock(BuildableModuleVersionSelectionResolveResult)

        when:
        def foundInCache = cache.supplyModuleVersions(newSelector("org", "lib", "1.0"), result)

        then:
        !foundInCache
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
        1 * result.resolved(suppliedMetaData, source)
    }

    def "caches and supplies remote and local metadata"() {
        def moduleSource = Stub(ModuleSource)
        def moduleMetaData = Stub(MutableModuleVersionMetaData)
        _ * moduleMetaData.copy() >> moduleMetaData
        def resolvedResult = Mock(BuildableModuleVersionMetaDataResolveResult.class) {
            getMetaData() >> moduleMetaData
            getModuleSource() >> moduleSource
            getState() >> BuildableModuleVersionMetaDataResolveResult.State.Resolved
        }
        def result = Mock(BuildableModuleVersionMetaDataResolveResult.class)

        given:
        cache.newDependencyResult(componentId("org", "remote", "1.0"), resolvedResult)

        when:
        def found = cache.supplyMetaData(componentId("org", "remote", "1.0"), result)

        then:
        found
        stats.metadataServed == 1
        1 * result.resolved(moduleMetaData, moduleSource)
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
}
