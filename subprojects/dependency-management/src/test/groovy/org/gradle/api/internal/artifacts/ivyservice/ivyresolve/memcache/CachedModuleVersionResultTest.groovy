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

import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult
import org.gradle.internal.component.model.ModuleSource
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetaData
import spock.lang.Specification

class CachedModuleVersionResultTest extends Specification {

    def "knows if result is cachable"() {
        def resolved = Mock(BuildableModuleComponentMetaDataResolveResult) {
            getState() >> BuildableModuleComponentMetaDataResolveResult.State.Resolved
            getMetaData() >> Stub(MutableModuleComponentResolveMetaData)
        }
        def missing = Mock(BuildableModuleComponentMetaDataResolveResult) { getState() >> BuildableModuleComponentMetaDataResolveResult.State.Missing }
        def probablyMissing = Mock(BuildableModuleComponentMetaDataResolveResult) { getState() >> BuildableModuleComponentMetaDataResolveResult.State.ProbablyMissing }
        def failed = Mock(BuildableModuleComponentMetaDataResolveResult) { getState() >> BuildableModuleComponentMetaDataResolveResult.State.Failed }

        expect:
        new CachedModuleVersionResult(resolved).cacheable
        new CachedModuleVersionResult(missing).cacheable
        new CachedModuleVersionResult(probablyMissing).cacheable
        !new CachedModuleVersionResult(failed).cacheable
    }

    def "interrogates result only when resolved"() {
        def resolved = Mock(BuildableModuleComponentMetaDataResolveResult)
        def missing = Mock(BuildableModuleComponentMetaDataResolveResult)

        when:
        new CachedModuleVersionResult(missing)

        then:
        1 * missing.getState() >> BuildableModuleComponentMetaDataResolveResult.State.Missing
        0 * missing._

        when:
        new CachedModuleVersionResult(resolved)

        then:
        1 * resolved.getState() >> BuildableModuleComponentMetaDataResolveResult.State.Resolved
        1 * resolved.getMetaData() >> Stub(MutableModuleComponentResolveMetaData)
    }

    def "supplies cached data"() {
        def suppliedMetaData = Mock(MutableModuleComponentResolveMetaData)
        def cachedMetaData = Mock(MutableModuleComponentResolveMetaData)
        def metaData = Mock(MutableModuleComponentResolveMetaData)
        def source = Mock(ModuleSource)
        def resolved = Mock(BuildableModuleComponentMetaDataResolveResult) {
            getState() >> BuildableModuleComponentMetaDataResolveResult.State.Resolved
            getMetaData() >> metaData
            getModuleSource() >> source
        }
        def missing = Mock(BuildableModuleComponentMetaDataResolveResult) { getState() >> BuildableModuleComponentMetaDataResolveResult.State.Missing }
        def probablyMissing = Mock(BuildableModuleComponentMetaDataResolveResult) { getState() >> BuildableModuleComponentMetaDataResolveResult.State.ProbablyMissing }

        def result = Mock(BuildableModuleComponentMetaDataResolveResult)

        when:
        def cached = new CachedModuleVersionResult(resolved)

        then:
        1 * metaData.copy() >> cachedMetaData

        when:
        cached.supply(result)

        then:
        1 * cachedMetaData.copy() >> suppliedMetaData
        1 * result.resolved(suppliedMetaData, source)

        when:
        new CachedModuleVersionResult(missing).supply(result)
        then:
        1 * result.missing()

        when:
        new CachedModuleVersionResult(probablyMissing).supply(result)
        then:
        1 * result.probablyMissing()
    }
}
