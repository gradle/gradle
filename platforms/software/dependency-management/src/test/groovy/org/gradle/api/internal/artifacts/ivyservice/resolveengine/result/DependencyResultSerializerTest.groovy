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

import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DependencyManagementTestUtil
import org.gradle.api.internal.artifacts.capability.CapabilitySelectorSerializer
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.resolve.ModuleVersionResolveException
import org.gradle.internal.resolve.caching.DesugaringAttributeContainerSerializer
import org.gradle.internal.serialize.InputStreamBackedDecoder
import org.gradle.internal.serialize.OutputStreamBackedEncoder
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector

class DependencyResultSerializerTest extends Specification {

    def serializer = new DependencyResultSerializer(
        new ComponentSelectionReasonSerializer(DependencyManagementTestUtil.componentSelectionDescriptorFactory()),
        new ComponentSelectorSerializer(
            new DesugaringAttributeContainerSerializer(
                AttributeTestUtil.attributesFactory(),
                TestUtil.objectInstantiator()
            ),
            new CapabilitySelectorSerializer()
        )
    )

    def "serializes successful dependency result"() {
        def requested = DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId("org", "foo"), new DefaultMutableVersionConstraint("1.0"))
        def successful = Mock(DependencyGraphEdge) {
            getRequested() >> requested
            getFromVariant() >> 55L
            getFailure() >> null
            getSelected() >> 12L
            getSelectedVariant() >> 123L
            getReason() >> ComponentSelectionReasons.requested()
        }

        when:
        def bytes = new ByteArrayOutputStream()
        def encoder = new OutputStreamBackedEncoder(bytes)
        serializer.write(encoder, successful)
        encoder.flush()
        def out = serializer.read(new InputStreamBackedDecoder(new ByteArrayInputStream(bytes.toByteArray())), [:])

        then:
        out.requested == requested
        out.failure == null
        out.fromVariant == 55L
        out.selected == 12L
        out.selectedVariant == 123L
    }

    def "serializes failed dependency result"() {
        def mid = DefaultModuleIdentifier.newId("x", "y")
        def requested = DefaultModuleComponentSelector.newSelector(mid, new DefaultMutableVersionConstraint("1.0"))
        def failure = new ModuleVersionResolveException(newSelector(mid, "1.2"), new RuntimeException("Boo!"))

        def failed = Mock(DependencyGraphEdge) {
            getRequested() >> requested
            getFromVariant() >> 55L
            getFailure() >> failure
            getReason() >> ComponentSelectionReasons.of(ComponentSelectionReasons.CONFLICT_RESOLUTION)
        }

        when:
        def bytes = new ByteArrayOutputStream()
        def encoder = new OutputStreamBackedEncoder(bytes)
        serializer.write(encoder, failed)
        encoder.flush()
        Map<ModuleComponentSelector, ModuleVersionResolveException> map = new HashMap<>()
        map.put(requested, failure)
        def out = serializer.read(new InputStreamBackedDecoder(new ByteArrayInputStream(bytes.toByteArray())), map)

        then:
        out.requested == requested
        out.failure.cause.message == "Boo!"
        out.fromVariant == 55L
        out.selected == null
        out.selectedVariant == null
        out.reason.conflictResolution
    }
}
