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

import javax.xml.namespace.QName
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.artifacts.ComponentMetadataDetails
import org.gradle.api.artifacts.ivy.IvyModuleDescriptor
import org.gradle.api.internal.artifacts.ivyservice.NamespaceId
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.internal.resolve.ModuleVersionResolveException
import org.gradle.internal.component.external.model.IvyModuleResolveMetaData
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetaData
import org.gradle.internal.reflect.DirectInstantiator
import spock.lang.Specification

class DefaultComponentMetadataHandlerTest extends Specification {
    def handler = new DefaultComponentMetadataHandler(new DirectInstantiator())

    def "processing fails when status is not present in status scheme"() {
        def metadata = Stub(MutableModuleComponentResolveMetaData) {
            getId() >> new DefaultModuleVersionIdentifier("group", "module", "version")
            getStatus() >> "green"
            getStatusScheme() >> ["alpha", "beta"]
        }

        when:
        handler.processMetadata(metadata)

        then:
        ModuleVersionResolveException e = thrown()
        e.message == /Unexpected status 'green' specified for group:module:version. Expected one of: [alpha, beta]/
    }

    def "supports rule with untyped ComponentMetaDataDetails parameter"() {
        def metadata = Stub(MutableModuleComponentResolveMetaData) {
            getId() >> new DefaultModuleVersionIdentifier("group", "module", "version")
            getStatus() >> "integration"
            getStatusScheme() >> ["integration", "release"]
        }
        def capturedDetails = null
        handler.eachComponent { details ->
            capturedDetails = details
        }

        when:
        handler.processMetadata(metadata)

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
        def metadata = Stub(MutableModuleComponentResolveMetaData) {
            getId() >> new DefaultModuleVersionIdentifier("group", "module", "version")
            getStatus() >> "integration"
            getStatusScheme() >> ["integration", "release"]
        }
        def capturedDetails = null
        handler.eachComponent { ComponentMetadataDetails details ->
            capturedDetails = details
        }

        when:
        handler.processMetadata(metadata)

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
        def id1 = new NamespaceId('namespace', 'info1')
        def id2 = new NamespaceId('namespace', 'info2')
        def metadata = Stub(TestIvyMetaData) {
            getId() >> new DefaultModuleVersionIdentifier("group", "module", "version")
            getStatus() >> "integration"
            getStatusScheme() >> ["integration", "release"]
            getExtraInfo() >> [(id1): "info1 value", (id2): "info2 value"]
            getBranch() >> "someBranch"
        }
        def capturedDescriptor = null
        handler.eachComponent { details, IvyModuleDescriptor descriptor ->
            capturedDescriptor = descriptor
        }

        when:
        handler.processMetadata(metadata)

        then:
        noExceptionThrown()
        capturedDescriptor instanceof IvyModuleDescriptor
        with(capturedDescriptor) {
            extraInfo.asMap() == [(new QName(id1.namespace, id1.name)): "info1 value", (new QName(id2.namespace, id2.name)): "info2 value"]
            branch == "someBranch"
            ivyStatus == "integration"
        }
    }

    def "rule with IvyModuleDescriptor parameter does not get invoked for non-Ivy components"() {
        def metadata = Stub(MutableModuleComponentResolveMetaData) {
            getId() >> new DefaultModuleVersionIdentifier("group", "module", "version")
            getStatus() >> "integration"
            getStatusScheme() >> ["integration", "release"]
        }

        def invoked = false
        handler.eachComponent { details, IvyModuleDescriptor descriptor ->
            invoked = true
        }

        when:
        handler.processMetadata(metadata)

        then:
        !invoked
    }

    def "complains if rule has no parameters"() {
        handler.eachComponent { -> }

        when:
        handler.processMetadata(Stub(MutableModuleComponentResolveMetaData))

        then:
        InvalidUserCodeException e = thrown()
        e.message == "A component metadata rule needs to have at least one parameter."
    }

    def "complains if first parameter type isn't assignment compatible with ComponentMetadataDetails"() {
        handler.eachComponent { String s -> }

        when:
        handler.processMetadata(Stub(MutableModuleComponentResolveMetaData))

        then:
        InvalidUserCodeException e = thrown()
        e.message == "First parameter of a component metadata rule needs to be of type 'ComponentMetadataDetails'."
    }

    def "complains if rule has unsupported parameter type"() {
        def metadata = Stub(MutableModuleComponentResolveMetaData) {
            getId() >> new DefaultModuleVersionIdentifier("group", "module", "version")
            getStatus() >> "integration"
            getStatusScheme() >> ["integration", "release"]
        }

        def invoked = false
        handler.eachComponent { details, String str ->
            invoked = true
        }

        when:
        handler.processMetadata(metadata)

        then:
        def e = thrown(InvalidUserCodeException)
        e.message == "Unsupported parameter type for component metadata rule: java.lang.String"
        !invoked
    }

    def "supports rule with multiple inputs in arbitrary order"() {
        def id1 = new NamespaceId('namespace', 'info1')
        def id2 = new NamespaceId('namespace', 'info2')
        def metadata = Stub(TestIvyMetaData) {
            getId() >> new DefaultModuleVersionIdentifier("group", "module", "version")
            getStatus() >> "integration"
            getStatusScheme() >> ["integration", "release"]
            getExtraInfo() >> [(id1): "info1 value", (id2): "info2 value"]
        }

        def capturedDetails1 = null
        def capturedDescriptor1 = null
        def capturedDescriptor2 = null

        handler.eachComponent { ComponentMetadataDetails details1, IvyModuleDescriptor descriptor1, IvyModuleDescriptor descriptor2  ->
            capturedDetails1 = details1
            capturedDescriptor1 = descriptor1
            capturedDescriptor2 = descriptor2
        }

        when:
        handler.processMetadata(metadata)

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
        capturedDescriptor1 instanceof IvyModuleDescriptor
        with(capturedDescriptor1) {
            extraInfo.asMap() == [(new QName(id1.namespace, id1.name)): "info1 value", (new QName(id2.namespace, id2.name)): "info2 value"]
        }
        capturedDescriptor2 instanceof IvyModuleDescriptor
        with(capturedDescriptor2) {
            extraInfo.asMap() == [(new QName(id1.namespace, id1.name)): "info1 value", (new QName(id2.namespace, id2.name)): "info2 value"]
        }
    }

    interface TestIvyMetaData extends IvyModuleResolveMetaData, MutableModuleComponentResolveMetaData {}
}
