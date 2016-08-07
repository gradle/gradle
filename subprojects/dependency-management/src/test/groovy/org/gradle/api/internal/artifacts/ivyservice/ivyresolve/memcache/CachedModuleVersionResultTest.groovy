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

import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult
import spock.lang.Specification

class CachedModuleVersionResultTest extends Specification {

    def "knows if result is cachable"() {
        def resolved = Mock(BuildableModuleComponentMetaDataResolveResult) {
            getState() >> BuildableModuleComponentMetaDataResolveResult.State.Resolved
            getMetaData() >> Stub(ModuleComponentResolveMetadata)
        }
        def missing = Mock(BuildableModuleComponentMetaDataResolveResult) {
            getState() >> BuildableModuleComponentMetaDataResolveResult.State.Missing
        }
        def failed = Mock(BuildableModuleComponentMetaDataResolveResult) {
            getState() >> BuildableModuleComponentMetaDataResolveResult.State.Failed
        }
        def unknown = Mock(BuildableModuleComponentMetaDataResolveResult) {
            getState() >> BuildableModuleComponentMetaDataResolveResult.State.Unknown
        }

        expect:
        new CachedModuleVersionResult(resolved).cacheable
        new CachedModuleVersionResult(missing).cacheable
        !new CachedModuleVersionResult(failed).cacheable
        !new CachedModuleVersionResult(unknown).cacheable
    }

    def "interrogates result only when resolved"() {
        def resolved = Mock(BuildableModuleComponentMetaDataResolveResult)
        def missing = Mock(BuildableModuleComponentMetaDataResolveResult)

        when:
        new CachedModuleVersionResult(missing)

        then:
        1 * missing.state >> BuildableModuleComponentMetaDataResolveResult.State.Missing
        1 * missing.authoritative >> true
        0 * missing._

        when:
        new CachedModuleVersionResult(resolved)

        then:
        1 * resolved.state >> BuildableModuleComponentMetaDataResolveResult.State.Resolved
        1 * resolved.metaData >> Stub(ModuleComponentResolveMetadata)
    }

    def "supplies cached data"() {
        def metaData = Mock(ModuleComponentResolveMetadata)
        def resolved = Mock(BuildableModuleComponentMetaDataResolveResult) {
            getState() >> BuildableModuleComponentMetaDataResolveResult.State.Resolved
            getMetaData() >> metaData
        }
        def missing = Mock(BuildableModuleComponentMetaDataResolveResult) {
            getState() >> BuildableModuleComponentMetaDataResolveResult.State.Missing
            isAuthoritative() >> true
        }
        def probablyMissing = Mock(BuildableModuleComponentMetaDataResolveResult) {
            getState() >> BuildableModuleComponentMetaDataResolveResult.State.Missing
            isAuthoritative() >> false
        }

        def result = Mock(BuildableModuleComponentMetaDataResolveResult)

        given:
        def cached = new CachedModuleVersionResult(resolved)

        when:
        cached.supply(result)

        then:
        1 * result.resolved(metaData)
        1 * result.setAuthoritative(false)
        0 * result._

        when:
        new CachedModuleVersionResult(missing).supply(result)

        then:
        1 * result.missing()
        1 * result.setAuthoritative(true)
        0 * result._

        when:
        new CachedModuleVersionResult(probablyMissing).supply(result)

        then:
        1 * result.missing()
        1 * result.setAuthoritative(false)
        0 * result._
    }
}
