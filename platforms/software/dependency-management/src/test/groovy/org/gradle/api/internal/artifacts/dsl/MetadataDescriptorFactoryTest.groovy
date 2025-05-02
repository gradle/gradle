/*
 * Copyright 2020 the original author or authors.
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

import com.google.common.collect.ImmutableMap
import org.gradle.api.artifacts.ivy.IvyModuleDescriptor
import org.gradle.api.artifacts.maven.PomModuleDescriptor
import org.gradle.api.internal.artifacts.ivyservice.NamespaceId
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.ivy.DefaultIvyModuleResolveMetadata
import org.gradle.internal.component.external.model.ivy.IvyModuleResolveMetadata
import org.gradle.internal.component.external.model.maven.MavenModuleResolveMetadata
import spock.lang.Specification

import javax.xml.namespace.QName

class MetadataDescriptorFactoryTest extends Specification {

    def 'exposes ivy descriptor if ivy metadata present'() {
        given:
        DefaultIvyModuleResolveMetadata metadata = Stub()
        def key = new NamespaceId("", "foo")
        metadata.getExtraAttributes() >> ImmutableMap.copyOf([(key): "bar"])
        metadata.getStatus() >> "status"
        metadata.getBranch() >> "branch"
        def factory = new MetadataDescriptorFactory(metadata)

        when:
        def descriptor = factory.createDescriptor(IvyModuleDescriptor)

        then:
        descriptor.branch == "branch"
        descriptor.ivyStatus == "status"
        descriptor.extraInfo.asMap() == [(new QName("foo")): "bar"]
    }

    def 'exposes pom descriptor if pom metadata present'() {
        given:
        MavenModuleResolveMetadata metadata = Stub()
        metadata.getPackaging() >> "pack"
        def factory = new MetadataDescriptorFactory(metadata)

        when:
        def descriptor = factory.createDescriptor(PomModuleDescriptor)

        then:
        descriptor.packaging == "pack"
    }

    def 'does not expose #name descriptor if no #name metadata present'() {
        given:
        def metadata = Mock(ModuleComponentResolveMetadata)
        def factory = new MetadataDescriptorFactory(metadata)

        when:
        def descriptor = factory.createDescriptor(descriptorType)

        then:
        descriptor == null

        where:
        name    | descriptorType
        "ivy"   | IvyModuleDescriptor
        "maven" | PomModuleDescriptor
    }

    def '#descriptorType and metadata #metadataType should match: #match'() {
        given:
        def metadata = Mock(metadataType)
        def factory = new MetadataDescriptorFactory(metadata)

        when:
        def actualMatch = factory.isMatchingMetadata(descriptorType, metadata)

        then:
        actualMatch == match

        where:
        metadataType               | descriptorType      | match
        IvyModuleResolveMetadata   | IvyModuleDescriptor | true
        MavenModuleResolveMetadata | PomModuleDescriptor | true
        MavenModuleResolveMetadata | IvyModuleDescriptor | false
        IvyModuleResolveMetadata   | PomModuleDescriptor | false
    }


}
