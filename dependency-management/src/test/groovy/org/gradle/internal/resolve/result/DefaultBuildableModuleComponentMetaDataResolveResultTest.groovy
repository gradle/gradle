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

package org.gradle.internal.resolve.result

import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata
import org.gradle.internal.resolve.ModuleVersionResolveException
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector

class DefaultBuildableModuleComponentMetaDataResolveResultTest extends Specification {
    def descriptor = new DefaultBuildableModuleComponentMetaDataResolveResult()

    def "has unknown state by default"() {
        expect:
        descriptor.state == BuildableModuleComponentMetaDataResolveResult.State.Unknown
        !descriptor.hasResult()
    }

    def "can mark as missing"() {
        when:
        descriptor.missing()

        then:
        descriptor.state == BuildableModuleComponentMetaDataResolveResult.State.Missing
        descriptor.failure == null
        descriptor.authoritative
        descriptor.hasResult()
    }

    def "can mark as failed"() {
        org.gradle.internal.Factory<String> broken = { "too bad" }
        def failure = new ModuleVersionResolveException(newSelector(DefaultModuleIdentifier.newId("a", "b"), "c"), broken)

        when:
        descriptor.failed(failure)

        then:
        descriptor.state == BuildableModuleComponentMetaDataResolveResult.State.Failed
        descriptor.failure == failure
        descriptor.authoritative
        descriptor.hasResult()
    }

    def "can mark as resolved using meta-data"() {
        def metaData = Stub(ModuleComponentResolveMetadata)

        when:
        descriptor.resolved(metaData)

        then:
        descriptor.state == BuildableModuleComponentMetaDataResolveResult.State.Resolved
        descriptor.failure == null
        descriptor.metaData == metaData
        descriptor.authoritative
        descriptor.hasResult()
    }

    def "can replace meta-data when resolved"() {
        def metaData = Stub(ModuleComponentResolveMetadata)
        def metaData2 = Stub(ModuleComponentResolveMetadata)

        descriptor.resolved(metaData)

        when:
        descriptor.setMetadata(metaData2)

        then:
        descriptor.state == BuildableModuleComponentMetaDataResolveResult.State.Resolved
        descriptor.failure == null
        descriptor.metaData == metaData2
        descriptor.authoritative
        descriptor.hasResult()
    }

    def "cannot get failure when has no result"() {
        when:
        descriptor.failure

        then:
        thrown(IllegalStateException)
    }

    def "cannot get meta-data when has no result"() {
        when:
        descriptor.metaData

        then:
        thrown(IllegalStateException)
    }

    def "cannot get authoritative flag when has no result"() {
        when:
        descriptor.authoritative

        then:
        thrown(IllegalStateException)
    }

    def "cannot get meta-data when failed"() {
        org.gradle.internal.Factory<String> broken = { "too bad" }
        given:
        def failure = new ModuleVersionResolveException(newSelector(DefaultModuleIdentifier.newId("a", "b"), "c"), broken)
        descriptor.failed(failure)

        when:
        descriptor.metaData

        then:
        ModuleVersionResolveException e = thrown()
        e == failure
    }

    def "cannot set metadata when not resolved"() {
        when:
        descriptor.metadata = Stub(ModuleComponentResolveMetadata)

        then:
        thrown(IllegalStateException)

        when:
        descriptor.missing()
        descriptor.metadata = Stub(ModuleComponentResolveMetadata)

        then:
        thrown(IllegalStateException)
    }
}
