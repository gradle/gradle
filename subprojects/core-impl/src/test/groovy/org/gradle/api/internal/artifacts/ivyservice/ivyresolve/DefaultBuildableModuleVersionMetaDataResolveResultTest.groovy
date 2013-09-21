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

import org.gradle.api.internal.artifacts.ivyservice.ModuleVersionResolveException
import org.gradle.api.internal.artifacts.metadata.MutableModuleVersionMetaData
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector

class DefaultBuildableModuleVersionMetaDataResolveResultTest extends Specification {
    def descriptor = new DefaultBuildableModuleVersionMetaDataResolveResult()
    def moduleSource = Stub(ModuleSource)

    def "has unknown state by default"() {
        expect:
        descriptor.state == BuildableModuleVersionMetaDataResolveResult.State.Unknown
    }

    def "can mark as missing"() {
        when:
        descriptor.missing()

        then:
        descriptor.state == BuildableModuleVersionMetaDataResolveResult.State.Missing
        descriptor.failure == null
    }

    def "can mark as probably missing"() {
        when:
        descriptor.probablyMissing()

        then:
        descriptor.state == BuildableModuleVersionMetaDataResolveResult.State.ProbablyMissing
        descriptor.failure == null
    }

    def "can mark as failed"() {
        def failure = new ModuleVersionResolveException(newSelector("a", "b", "c"), "broken")

        when:
        descriptor.failed(failure)

        then:
        descriptor.state == BuildableModuleVersionMetaDataResolveResult.State.Failed
        descriptor.failure == failure
    }

    def "can mark as resolved using meta-data"() {
        def metaData = Stub(MutableModuleVersionMetaData)

        when:
        descriptor.resolved(metaData, moduleSource)

        then:
        descriptor.state == BuildableModuleVersionMetaDataResolveResult.State.Resolved
        descriptor.failure == null
        descriptor.metaData == metaData
        descriptor.moduleSource == moduleSource
    }

    def "cannot get failure when not resolved"() {
        when:
        descriptor.failure

        then:
        thrown(IllegalStateException)
    }

    def "cannot get meta-data when not resolved"() {
        when:
        descriptor.metaData

        then:
        thrown(IllegalStateException)
    }

    def "cannot get meta-data when failed"() {
        given:
        def failure = new ModuleVersionResolveException(newSelector("a", "b", "c"), "broken")
        descriptor.failed(failure)

        when:
        descriptor.metaData

        then:
        ModuleVersionResolveException e = thrown()
        e == failure
    }

    def "cannot get module source when failed"() {
        given:
        def failure = new ModuleVersionResolveException(newSelector("a", "b", "c"), "broken")
        descriptor.failed(failure)

        when:
        descriptor.getModuleSource()

        then:
        ModuleVersionResolveException e = thrown()
        e == failure
    }

    def "cannot set module source when failed"() {
        given:
        def failure = new ModuleVersionResolveException(newSelector("a", "b", "c"), "broken")
        descriptor.failed(failure)

        when:
        descriptor.setModuleSource(Mock(ModuleSource))

        then:
        ModuleVersionResolveException e = thrown()
        e == failure
    }

    def "cannot get module source when missing"() {
        given:
        descriptor.missing()

        when:
        descriptor.getModuleSource()

        then:
        thrown(IllegalStateException)
    }

    def "cannot set module source when missing"() {
        given:
        descriptor.missing()

        when:
        descriptor.setModuleSource(Mock(ModuleSource))

        then:
        thrown(IllegalStateException)
    }

    def "cannot get module source when probably missing"() {
        given:
        descriptor.probablyMissing()

        when:
        descriptor.getModuleSource()

        then:
        thrown(IllegalStateException)
    }

    def "cannot set module source when probably missing"() {
        given:
        descriptor.probablyMissing()

        when:
        descriptor.setModuleSource(Mock(ModuleSource))

        then:
        thrown(IllegalStateException)
    }
}
