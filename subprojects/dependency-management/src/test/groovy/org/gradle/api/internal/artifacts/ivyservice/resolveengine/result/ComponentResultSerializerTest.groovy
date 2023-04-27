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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result

import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.attributes.Attribute
import org.gradle.api.capabilities.Capability
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DependencyManagementTestUtil
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphComponent
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.model.ComponentGraphResolveMetadata
import org.gradle.internal.component.model.ComponentGraphResolveState
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier.newId

class ComponentResultSerializerTest extends Specification {

    private ComponentIdentifierSerializer componentIdentifierSerializer = new ComponentIdentifierSerializer()
    def serializer = new ComponentResultSerializer(
        new ThisBuildOnlyComponentDetailsSerializer(),
        new ResolvedVariantResultSerializer(
            componentIdentifierSerializer,
            new DesugaredAttributeContainerSerializer(AttributeTestUtil.attributesFactory(), TestUtil.objectInstantiator())
        ),
        DependencyManagementTestUtil.componentSelectionDescriptorFactory(),
        false
    )

    def "serializes"() {
        def componentIdentifier = new DefaultModuleComponentIdentifier(DefaultModuleIdentifier.newId('group', 'module'), 'version')
        def extId = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId('group', 'external'), '1.0')
        def attributes = AttributeTestUtil.attributesFactory().mutable()
        attributes.attribute(Attribute.of('type', String), 'custom')
        attributes.attribute(Attribute.of('format', String), 'jar')
        def v1 = Mock(ResolvedVariantResult) {
            getOwner() >> componentIdentifier
            getDisplayName() >> "v1"
            getAttributes() >> ImmutableAttributes.EMPTY
            getCapabilities() >> [capability('foo')]
            getExternalVariant() >> Optional.empty()
        }
        def v3 = Mock(ResolvedVariantResult) {
            getOwner() >> extId
            getDisplayName() >> "v3"
            getAttributes() >> attributes
            getCapabilities() >> [capability('bar'), capability('baz')]
            getExternalVariant() >> Optional.empty()
        }
        def v2 = Mock(ResolvedVariantResult) {
            getOwner() >> componentIdentifier
            getDisplayName() >> "v2"
            getAttributes() >> attributes
            getCapabilities() >> [capability('bar'), capability('baz')]
            getExternalVariant() >> Optional.of(v3)
        }
        def componentMetadata = Stub(ComponentGraphResolveMetadata) {
            moduleVersionId >> newId('org', 'foo', '2.0')
        }
        def componentState = Stub(ComponentGraphResolveState) {
            id >> componentIdentifier
            metadata >> componentMetadata
            repositoryName >> 'repoName'
        }
        def component = Stub(ResolvedGraphComponent) {
            resolveState >> componentState
            selectionReason >> ComponentSelectionReasons.requested()
            selectedVariants >> [v1, v2]
        }

        when:
        def serialized = serialize(component)
        def result = deserialize(serialized)

        then:
        result.id == componentIdentifier
        result.selectionReason == ComponentSelectionReasons.requested()
        result.moduleVersion == newId('org', 'foo', '2.0')
        for (def variants : [result.selectedVariants, result.availableVariants]) {
            variants.size() == 2
            variants[0].displayName == 'v1'
            variants[0].attributes == ImmutableAttributes.EMPTY
            variants[0].capabilities.size() == 1
            variants[0].capabilities[0].group == 'org'
            variants[0].capabilities[0].name == 'foo'
            variants[0].capabilities[0].version == '1.0'
            variants[0].owner == componentIdentifier
            variants[1].displayName == 'v2'
            variants[1].attributes == attributes.asImmutable()
            variants[1].capabilities.size() == 2
            variants[1].capabilities[0].group == 'org'
            variants[1].capabilities[0].name == 'bar'
            variants[1].capabilities[0].version == '1.0'
            variants[1].capabilities[1].group == 'org'
            variants[1].capabilities[1].name == 'baz'
            variants[1].capabilities[1].version == '1.0'
            variants[1].owner == componentIdentifier
            variants[1].externalVariant.present
            def external = variants[1].externalVariant.get()
            external.displayName == 'v3'
            external.attributes == attributes.asImmutable()
            external.capabilities.size() == 2
            external.capabilities[0].group == 'org'
            external.capabilities[0].name == 'bar'
            external.capabilities[0].version == '1.0'
            external.capabilities[1].group == 'org'
            external.capabilities[1].name == 'baz'
            external.capabilities[1].version == '1.0'
            external.owner == extId
        }
        result.repositoryId == 'repoName'
    }

    private byte[] serialize(ResolvedGraphComponent component) {
        def outstr = new ByteArrayOutputStream()
        def encoder = new KryoBackedEncoder(outstr)
        serializer.write(encoder, component)
        encoder.flush()
        return outstr.toByteArray()
    }

    private ResolvedComponentResult deserialize(byte[] serialized) {
        def builder = new DefaultResolutionResultBuilder()
        serializer.readInto(new KryoBackedDecoder(new ByteArrayInputStream(serialized)), builder)
        return builder.complete(0).root
    }

    private Capability capability(String name) {
        Mock(Capability) {
            getGroup() >> 'org'
            getName() >> name
            getVersion() >> '1.0'
        }
    }
}
