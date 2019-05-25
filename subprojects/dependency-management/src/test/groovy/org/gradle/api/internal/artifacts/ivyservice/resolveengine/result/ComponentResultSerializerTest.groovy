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

import org.gradle.api.attributes.Attribute
import org.gradle.api.capabilities.Capability
import org.gradle.api.internal.artifacts.DefaultImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedVariantDetails
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.model.NamedObjectInstantiator
import org.gradle.internal.Describables
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.serialize.SerializerSpec
import org.gradle.util.AttributeTestUtil

import static org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier.newId

class ComponentResultSerializerTest extends SerializerSpec {

    def serializer = new ComponentResultSerializer(new DefaultImmutableModuleIdentifierFactory(), new DesugaredAttributeContainerSerializer(AttributeTestUtil.attributesFactory(), NamedObjectInstantiator.INSTANCE))

    def "serializes"() {
        def componentIdentifier = new DefaultModuleComponentIdentifier(DefaultModuleIdentifier.newId('group', 'module'), 'version')
        def attributes = AttributeTestUtil.attributesFactory().mutable()
        attributes.attribute(Attribute.of('type', String), 'custom')
        attributes.attribute(Attribute.of('format', String), 'jar')
        def v1 = Mock(ResolvedVariantDetails) {
            getVariantName() >> Describables.of("v1")
            getVariantAttributes() >> ImmutableAttributes.EMPTY
            getCapabilities() >> [capability('foo')]
        }
        def v2 = Mock(ResolvedVariantDetails) {
            getVariantName() >> Describables.of("v2")
            getVariantAttributes() >> attributes
            getCapabilities() >> [capability('bar'), capability('baz')]
        }
        def selection = new DetachedComponentResult(12L,
            newId('org', 'foo', '2.0'),
            ComponentSelectionReasons.requested(),
            componentIdentifier, [v1, v2],
            'repoName')

        when:
        def result = serialize(selection, serializer)

        then:
        result.resultId == 12L
        result.selectionReason == ComponentSelectionReasons.requested()
        result.moduleVersion == newId('org', 'foo', '2.0')
        result.componentId == componentIdentifier
        result.resolvedVariants.size() == 2
        result.resolvedVariants[0].variantName.displayName == 'v1'
        result.resolvedVariants[0].variantAttributes == ImmutableAttributes.EMPTY
        result.resolvedVariants[0].capabilities.size() == 1
        result.resolvedVariants[0].capabilities[0].group== 'org'
        result.resolvedVariants[0].capabilities[0].name == 'foo'
        result.resolvedVariants[0].capabilities[0].version == '1.0'
        result.resolvedVariants[1].variantName.displayName == 'v2'
        result.resolvedVariants[1].variantAttributes == attributes.asImmutable()
        result.resolvedVariants[1].capabilities.size() == 2
        result.resolvedVariants[1].capabilities[0].group== 'org'
        result.resolvedVariants[1].capabilities[0].name == 'bar'
        result.resolvedVariants[1].capabilities[0].version == '1.0'
        result.resolvedVariants[1].capabilities[1].group== 'org'
        result.resolvedVariants[1].capabilities[1].name == 'baz'
        result.resolvedVariants[1].capabilities[1].version == '1.0'
        result.repositoryName == 'repoName'
    }

    private Capability capability(String name) {
        Mock(Capability) {
            getGroup() >> 'org'
            getName() >> name
            getVersion() >> '1.0'
        }
    }
}
