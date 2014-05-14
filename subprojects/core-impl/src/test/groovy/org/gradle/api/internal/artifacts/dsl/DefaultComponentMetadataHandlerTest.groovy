/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.dsl
import org.gradle.api.GradleException
import org.gradle.api.artifacts.ComponentMetadataDetails
import org.gradle.api.artifacts.IvyModuleDescriptor
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ModuleVersionResolveException
import org.gradle.api.internal.artifacts.metadata.IvyModuleVersionMetaData
import org.gradle.api.internal.artifacts.metadata.MutableModuleVersionMetaData
import org.gradle.internal.reflect.DirectInstantiator
import spock.lang.Specification

class DefaultComponentMetadataHandlerTest extends Specification {
    def handler = new DefaultComponentMetadataHandler(new DirectInstantiator())

    def "processing fails when status is not present in status scheme"() {
        def metadata = Stub(MutableModuleVersionMetaData) {
            getId() >> new DefaultModuleVersionIdentifier("group", "module", "version")
            getStatus() >> "green"
            getStatusScheme() >> ["alpha", "beta"]
        }

        when:
        handler.process(metadata)

        then:
        ModuleVersionResolveException e = thrown()
        e.message == /Unexpected status 'green' specified for group:module:version. Expected one of: [alpha, beta]/
    }

    def "supports rule with untyped ComponentMetaDataDetails parameter"() {
        def metadata = Stub(MutableModuleVersionMetaData) {
            getId() >> new DefaultModuleVersionIdentifier("group", "module", "version")
            getStatus() >> "integration"
            getStatusScheme() >> ["integration", "release"]
        }
        def capturedDetails = null
        handler.eachComponent { details ->
            capturedDetails = details
        }

        when:
        handler.process(metadata)

        then:
        noExceptionThrown()
        capturedDetails instanceof ComponentMetadataDetails
        with(capturedDetails) {
            id.group == "group"
            id.name == "module"
            id.version == "version"
            status == "integration"
            statusScheme == ["integration", "release"]
        }
    }

    def "supports rule with typed ComponentMetaDataDetails parameter"() {
        def metadata = Stub(MutableModuleVersionMetaData) {
            getId() >> new DefaultModuleVersionIdentifier("group", "module", "version")
            getStatus() >> "integration"
            getStatusScheme() >> ["integration", "release"]
        }
        def capturedDetails = null
        handler.eachComponent { ComponentMetadataDetails details ->
            capturedDetails = details
        }

        when:
        handler.process(metadata)

        then:
        noExceptionThrown()
        capturedDetails instanceof ComponentMetadataDetails
        with(capturedDetails) {
            id.group == "group"
            id.name == "module"
            id.version == "version"
            status == "integration"
            statusScheme == ["integration", "release"]
        }
    }

    def "supports rule with typed IvyModuleDescriptor parameter"() {
        def metadata = Stub(TestIvyMetaData) {
            getId() >> new DefaultModuleVersionIdentifier("group", "module", "version")
            getStatus() >> "integration"
            getStatusScheme() >> ["integration", "release"]
            getExtraInfo() >> [info1: "info1 value", info2: "info2 value"]
        }
        def capturedDescriptor = null
        handler.eachComponent { details, IvyModuleDescriptor descriptor ->
            capturedDescriptor = descriptor
        }

        when:
        handler.process(metadata)

        then:
        noExceptionThrown()
        capturedDescriptor instanceof IvyModuleDescriptor
        with(capturedDescriptor) {
            extraInfo == [info1: "info1 value", info2: "info2 value"]
        }
    }

    def "rule with IvyModuleDescriptor parameter does not get invoked for non-Ivy components"() {
        def metadata = Stub(MutableModuleVersionMetaData) {
            getId() >> new DefaultModuleVersionIdentifier("group", "module", "version")
            getStatus() >> "integration"
            getStatusScheme() >> ["integration", "release"]
        }

        def invoked = false
        handler.eachComponent { details, IvyModuleDescriptor descriptor ->
            invoked = true
        }

        when:
        handler.process(metadata)

        then:
        !invoked
    }

    def "complains if rule has unsupported parameter type"() {
        def metadata = Stub(MutableModuleVersionMetaData) {
            getId() >> new DefaultModuleVersionIdentifier("group", "module", "version")
            getStatus() >> "integration"
            getStatusScheme() >> ["integration", "release"]
        }

        def invoked = false
        handler.eachComponent { details, String str ->
            invoked = true
        }

        when:
        handler.process(metadata)

        then:
        def e = thrown(GradleException)
        e.message == "Unsupported parameter type for component metadata rule: java.lang.String"
        !invoked
    }

    def "supports rule with multiple parameters in arbitrary order"() {
        def metadata = Stub(TestIvyMetaData) {
            getId() >> new DefaultModuleVersionIdentifier("group", "module", "version")
            getStatus() >> "integration"
            getStatusScheme() >> ["integration", "release"]
            getExtraInfo() >> [info1: "info1 value", info2: "info2 value"]
        }

        def capturedDetails1 = null
        def capturedDetails2 = null
        def capturedDescriptor1 = null
        def capturedDescriptor2 = null

        handler.eachComponent { IvyModuleDescriptor descriptor1, details1, IvyModuleDescriptor descriptor2, ComponentMetadataDetails details2  ->
            capturedDetails1 = details1
            capturedDetails2 = details2
            capturedDescriptor1 = descriptor1
            capturedDescriptor2 = descriptor2
        }

        when:
        handler.process(metadata)

        then:
        noExceptionThrown()
        capturedDetails1 instanceof ComponentMetadataDetails
        with(capturedDetails1) {
            id.group == "group"
            id.name == "module"
            id.version == "version"
            status == "integration"
            statusScheme == ["integration", "release"]
        }
        capturedDetails2.is(capturedDetails1)
        capturedDescriptor1 instanceof IvyModuleDescriptor
        with(capturedDescriptor1) {
            extraInfo == [info1: "info1 value", info2: "info2 value"]
        }
        capturedDescriptor2 instanceof IvyModuleDescriptor
        with(capturedDescriptor2) {
            extraInfo == [info1: "info1 value", info2: "info2 value"]
        }
    }

    interface TestIvyMetaData extends IvyModuleVersionMetaData, MutableModuleVersionMetaData {}
}
