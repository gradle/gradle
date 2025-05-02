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
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DependencyManagementTestUtil
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphComponent
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphVariant
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.model.ComponentGraphResolveMetadata
import org.gradle.internal.component.model.ComponentGraphResolveState
import org.gradle.internal.component.model.VariantGraphResolveState
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import org.gradle.util.AttributeTestUtil
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier.newId

class ThisBuildTreeOnlyComponentResultSerializerTest extends Specification {

    def serializer = new ThisBuildTreeOnlyComponentResultSerializer(
        DependencyManagementTestUtil.componentSelectionDescriptorFactory()
    )

    def "serializes"() {
        def componentIdentifier = new DefaultModuleComponentIdentifier(DefaultModuleIdentifier.newId('group', 'module'), 'version')
        def extId = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId('group', 'external'), '1.0')
        def attributes = AttributeTestUtil.attributesFactory().mutable()
        attributes.attribute(Attribute.of('type', String), 'custom')
        attributes.attribute(Attribute.of('format', String), 'jar')
        def v1Result = Mock(ResolvedVariantResult)
        def v1State = Mock(VariantGraphResolveState) {
            getInstanceId() >> 1
        }
        def v1 = Mock(ResolvedGraphVariant) {
            getNodeId() >> 1
            getOwner() >> componentIdentifier
            getResolveState() >> v1State
            getExternalVariant() >> null
        }
        def v3Result = Mock(ResolvedVariantResult)
        def v3State = Mock(VariantGraphResolveState) {
            getInstanceId() >> 3
        }
        def v3 = Mock(ResolvedGraphVariant) {
            getNodeId() >> 3
            getOwner() >> extId
            getResolveState() >> v3State
            getComponentResolveState() >> Mock(ComponentGraphResolveState)
            getExternalVariant() >> null
        }
        def v2Result = Mock(ResolvedVariantResult)
        def v2State = Mock(VariantGraphResolveState) {
            getInstanceId() >> 2
        }
        def v2 = Mock(ResolvedGraphVariant) {
            getNodeId() >> 2
            getOwner() >> componentIdentifier
            getResolveState() >> v2State
            getExternalVariant() >> v3
        }
        def componentMetadata = Stub(ComponentGraphResolveMetadata) {
            moduleVersionId >> newId('org', 'foo', '2.0')
        }
        def componentState = Stub(ComponentGraphResolveState) {
            id >> componentIdentifier
            metadata >> componentMetadata
            getPublicViewFor(v1State, _) >> v1Result
            getPublicViewFor(v2State, _) >> v2Result
        }
        def component = Stub(ResolvedGraphComponent) {
            resolveState >> componentState
            selectionReason >> ComponentSelectionReasons.requested()
            selectedVariants >> [v1, v2]
            repositoryName >> 'repoName'
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
            variants[0] == v1Result
            variants[1] == v2Result
        }
        result.repositoryId == 'repoName'
    }

    private byte[] serialize(ResolvedGraphComponent component) {
        def outstr = new ByteArrayOutputStream()
        def encoder = new KryoBackedEncoder(outstr)
        serializer.writeComponentResult(encoder, component, false)
        encoder.flush()
        return outstr.toByteArray()
    }

    private ResolvedComponentResult deserialize(byte[] serialized) {
        def builder = new ResolutionResultGraphBuilder()
        serializer.readComponentResult(new KryoBackedDecoder(new ByteArrayInputStream(serialized)), builder)
        return builder.getRoot(0)
    }
}
