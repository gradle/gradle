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

import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.BuildableModuleVersionMetaDataResolveResult
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleVersionMetaData
import spock.lang.Specification

/**
 * By Szczepan Faber on 4/19/13
 */
class CachedModuleVersionResultTest extends Specification {

    def "knows if result is cachable"() {
        def resolved = Mock(BuildableModuleVersionMetaDataResolveResult) {
            getState() >> BuildableModuleVersionMetaDataResolveResult.State.Resolved
            getMetaData() >> Stub(ModuleVersionMetaData)
        }
        def missing = Mock(BuildableModuleVersionMetaDataResolveResult) { getState() >> BuildableModuleVersionMetaDataResolveResult.State.Missing }
        def probablyMissing = Mock(BuildableModuleVersionMetaDataResolveResult) { getState() >> BuildableModuleVersionMetaDataResolveResult.State.ProbablyMissing }
        def failed = Mock(BuildableModuleVersionMetaDataResolveResult) { getState() >> BuildableModuleVersionMetaDataResolveResult.State.Failed }

        expect:
        new CachedModuleVersionResult(resolved).cacheable
        new CachedModuleVersionResult(missing).cacheable
        new CachedModuleVersionResult(probablyMissing).cacheable
        !new CachedModuleVersionResult(failed).cacheable
    }

    def "interrogates result only when resolved"() {
        def resolved = Mock(BuildableModuleVersionMetaDataResolveResult)
        def missing = Mock(BuildableModuleVersionMetaDataResolveResult)

        when:
        new CachedModuleVersionResult(missing)

        then:
        1 * missing.getState() >> BuildableModuleVersionMetaDataResolveResult.State.Missing
        0 * missing._

        when:
        new CachedModuleVersionResult(resolved)

        then:
        1 * resolved.getState() >> BuildableModuleVersionMetaDataResolveResult.State.Resolved
        1 * resolved.getMetaData() >> Stub(ModuleVersionMetaData)
    }

    def "supplies cached data"() {
        def resolved = Mock(BuildableModuleVersionMetaDataResolveResult) {
            getState() >> BuildableModuleVersionMetaDataResolveResult.State.Resolved
            getMetaData() >> Stub(ModuleVersionMetaData)
        }
        def missing = Mock(BuildableModuleVersionMetaDataResolveResult) { getState() >> BuildableModuleVersionMetaDataResolveResult.State.Missing }
        def probablyMissing = Mock(BuildableModuleVersionMetaDataResolveResult) { getState() >> BuildableModuleVersionMetaDataResolveResult.State.ProbablyMissing }

        def result = Mock(BuildableModuleVersionMetaDataResolveResult)

        when:
        new CachedModuleVersionResult(resolved).supply(result)
        then:
        1 * result.resolved(_, _, _, _)

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
