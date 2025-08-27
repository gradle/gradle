/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform

import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.internal.artifacts.TransformRegistration
import org.gradle.api.internal.artifacts.VariantTransformRegistry
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.AttributeSchemaServices
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.api.internal.attributes.DefaultAttributesSchema
import org.gradle.internal.isolation.TestIsolatableFactory
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil
import spock.lang.Issue
import spock.lang.Specification

import javax.inject.Inject

class ConsumerProvidedVariantFinderTest extends Specification {

    AttributeSchemaServices services = AttributeTestUtil.services()
    AttributesSchemaInternal schema = TestUtil.newInstance(DefaultAttributesSchema, new TestIsolatableFactory())
    VariantTransformRegistry transformRegistry = Mock(VariantTransformRegistry)
    ConsumerProvidedVariantFinder transformations = new ConsumerProvidedVariantFinder(
        transformRegistry,
        schema,
        AttributeTestUtil.attributesFactory(),
        services
    )

    def "selects transform that can produce variant that is compatible with requested"() {
        def requested = AttributeTestUtil.attributes([usage: "requested"])

        def fromSource = AttributeTestUtil.attributes(usage: "fromSource")
        def compatible = AttributeTestUtil.attributes(usage: "compatible")
        def incompatible = AttributeTestUtil.attributes(usage: "incompatible")

        def transform1 = registration(fromSource, incompatible)
        def transform2 = registration(fromSource, compatible)
        def transform3 = registration(compatible, incompatible)

        def sourceVariant = variant([usage: "source"])
        def otherVariant = variant([usage: "other"])
        def variants = [ sourceVariant, otherVariant ]

        allowEdge("usage", "source", "fromSource")
        allowEdge("usage", "compatible", "requested")

        given:
        transformRegistry.registrations >> [transform1, transform2, transform3]

        when:
        def result = transformations.findCandidateTransformationChains(variants, requested)

        then:
        result.size() == 1
        assertTransformChain(result.first(), sourceVariant, compatible, transform2)
    }

    def "selects all transforms that can produce variant that is compatible with requested"() {
        def requested = AttributeTestUtil.attributes([usage: "requested"])

        def fromSource = AttributeTestUtil.attributes(usage: "fromSource")
        def fromOther = AttributeTestUtil.attributes(usage: "fromOther")
        def compatible = AttributeTestUtil.attributes(usage: "compatible")
        def compatible2 = AttributeTestUtil.attributes(usage: "compatible2")

        def transform1 = registration(fromSource, compatible)
        def transform2 = registration(fromSource, compatible2)
        def transform3 = registration(fromOther, compatible2)
        def transform4 = registration(fromOther, compatible)

        def sourceVariant = variant([usage: "source"])
        def otherVariant = variant([usage: "other"])
        def variants = [ sourceVariant, otherVariant ]

        allowEdge("usage", "source", "fromSource")
        allowEdge("usage", "other", "fromOther")
        allowEdge("usage", "compatible", "requested")
        allowEdge("usage", "compatible2", "requested")

        given:
        transformRegistry.registrations >> [transform1, transform2, transform3, transform4]

        when:
        def result = transformations.findCandidateTransformationChains(variants, requested)

        then:
        result.size() == 4
        assertTransformChain(result[0], otherVariant, compatible, transform4)
        assertTransformChain(result[1], sourceVariant, compatible, transform1)
        assertTransformChain(result[2], otherVariant, compatible2, transform3)
        assertTransformChain(result[3], sourceVariant, compatible2, transform2)
    }

    def "selects chain of transforms that can produce variant that is compatible with requested"() {
        def requested = AttributeTestUtil.attributes([usage: "requested"])

        def fromSource = AttributeTestUtil.attributes(usage: "fromSource")
        def fromOther = AttributeTestUtil.attributes([usage: "fromOther"])

        def fromIntermediate = AttributeTestUtil.attributes([usage: "fromIntermediate"])
        def intermediate = AttributeTestUtil.attributes(usage: "intermediate")

        def fromIntermediate2 = AttributeTestUtil.attributes([usage: "fromIntermediate2"])
        def intermediate2 = AttributeTestUtil.attributes([usage: "intermediate2"])

        def compatible = AttributeTestUtil.attributes([usage: "compatible"])
        def compatible2 = AttributeTestUtil.attributes([usage: "compatible2"])

        def incompatible = AttributeTestUtil.attributes(usage: "incompatible")
        def incompatible2 = AttributeTestUtil.attributes([usage: "incompatible2"])

        def sourceVariant = variant([usage: "source"])
        def otherVariant = variant([usage: "other"])
        def variants = [ sourceVariant, otherVariant ]

        def transform1 = registration(fromSource, incompatible)
        def transform2 = registration(fromSource, intermediate)
        def transform3 = registration(fromIntermediate, compatible)
        def transform4 = registration(fromOther, intermediate2)
        def transform5 = registration(fromIntermediate2, compatible2)
        def transform6 = registration(fromIntermediate2, incompatible2)

        allowEdge("usage", "source", "fromSource")
        allowEdge("usage", "other", "fromOther")
        allowEdge("usage", "intermediate", "fromIntermediate")
        allowEdge("usage", "intermediate2", "fromIntermediate2")
        allowEdge("usage", "compatible", "requested")
        allowEdge("usage", "compatible2", "requested")

        given:
        transformRegistry.registrations >> [transform1, transform2, transform3, transform4, transform5, transform6]

        when:
        def result = transformations.findCandidateTransformationChains(variants, requested)

        then:
        result.size() == 2
        // sourceVariant + transform2 + transform3 = compatible
        assertTransformChain(result[0], sourceVariant, compatible, transform2, transform3)
        // otherVariant + transform2 + transform4 = compatible2
        assertTransformChain(result[1], otherVariant, compatible2, transform4, transform5)
    }

    def "prefers direct transformation over indirect"() {
        def requested = AttributeTestUtil.attributes([usage: "requested"])

        def fromSource = AttributeTestUtil.attributes([usage: "fromSource"])
        def fromOther = AttributeTestUtil.attributes([usage: "fromOther"])

        def compatible = AttributeTestUtil.attributes([usage: "compatible"])
        def compatible2 = AttributeTestUtil.attributes([usage: "compatible2"])
        def compatibleIndirect = AttributeTestUtil.attributes(usage: "compatibleIndirect")
        def fromIndirect = AttributeTestUtil.attributes(usage: "fromIndirect")
        def incompatible = AttributeTestUtil.attributes(usage: "incompatible")

        def sourceVariant = variant([usage: "source"])
        def otherVariant = variant([usage: "other"])
        def variants = [ sourceVariant, otherVariant ]

        def transform1 = registration(fromIndirect, incompatible)
        def transform2 = registration(fromIndirect, compatibleIndirect)
        def transform3 = registration(fromSource, compatible)
        def transform4 = registration(fromOther, compatible2)

        allowEdge("usage", "source", "fromSource")
        allowEdge("usage", "other", "fromOther")
        allowEdge("usage", "compatibleIndirect", "requested")
        allowEdge("usage", "compatible", "requested")
        allowEdge("usage", "compatible2", "requested")

        // We do not expect these edges to be traversed, but permit them to ensure we don't try to use them
        allowEdge("usage", "compatible", "fromIndirect")
        allowEdge("usage", "compatible2", "fromIndirect")

        given:
        transformRegistry.registrations >> [transform1, transform2, transform3, transform4]

        when:
        def result = transformations.findCandidateTransformationChains(variants, requested)

        then:
        result.size() == 2
        assertTransformChain(result[0], sourceVariant, compatible, transform3)
        assertTransformChain(result[1], otherVariant, compatible2, transform4)
    }

    def "prefers shortest chain of transforms #registrationsIndex"() {
        def requested = AttributeTestUtil.attributes([usage: "requested"])

        def fromSource = AttributeTestUtil.attributes(usage: "fromSource")
        def fromOther = AttributeTestUtil.attributes(usage: "fromOther")

        def intermediate = AttributeTestUtil.attributes([usage: "intermediate"])

        def compatible = AttributeTestUtil.attributes([usage: "compatible"])

        def sourceVariant = variant([usage: "source"])
        def variants = [ sourceVariant ]

        def transform1 = registration(fromSource, fromOther)
        def transform2 = registration(fromSource, intermediate)
        def transform3 = registration(fromOther, intermediate)
        def transform4 = registration(intermediate, compatible)
        def registrations = [transform1, transform2, transform3, transform4]

        allowEdge("usage", "source", "fromSource")
        allowEdge("usage", "compatible", "requested")

        // We do not expect this edge to be traversed, but permit it to ensure we don't try to use it
        allowEdge("usage", "intermediate", "fromIntermediate")

        given:
        transformRegistry.registrations >> [registrations[registrationsIndex[0]], registrations[registrationsIndex[1]], registrations[registrationsIndex[2]], registrations[registrationsIndex[3]]]

        when:
        def result = transformations.findCandidateTransformationChains(variants, requested)

        then:
        result.size() == 1
        assertTransformChain(result.first(), sourceVariant, compatible, transform2, transform4)

        where:
        registrationsIndex << (0..3).permutations()
    }

    @Issue("gradle/gradle#7061")
    def "selects chain of transforms that only all the attributes are satisfied"() {
        def requested = AttributeTestUtil.attributes([usage: "requested", other: "transform3"])

        def fromSource = AttributeTestUtil.attributes(usage: "fromSource", other: "fromSource")
        def fromIntermediate = AttributeTestUtil.attributes([usage: "fromIntermediate"])

        def incompatible = AttributeTestUtil.attributes([usage: "incompatible"])
        def intermediate = AttributeTestUtil.attributes([usage: "intermediate", other: "transform2"])
        def compatible = AttributeTestUtil.attributes([usage: "compatible", other: "transform3"])

        def sourceVariant = variant([usage: "source"])
        def variants = [ sourceVariant ]

        def transform1 = registration(fromSource, incompatible)
        def transform2 = registration(fromSource, intermediate)
        def transform3 = registration(fromIntermediate, compatible)

        allowEdge("usage", "source", "fromSource")
        allowEdge("usage", "intermediate", "fromIntermediate")
        allowEdge("usage", "compatible", "requested")

        given:
        transformRegistry.registrations >> [transform1, transform2, transform3]

        when:
        def result = transformations.findCandidateTransformationChains(variants, requested)

        then:
        result.size() == 1
        assertTransformChain(result.first(), sourceVariant, compatible, transform2, transform3)
    }

    def "returns empty list when no transforms are available to produce requested variant"() {
        def requested = AttributeTestUtil.attributes([usage: "requested"])

        def fromSource = AttributeTestUtil.attributes(usage: "fromSource")
        def incompatible = AttributeTestUtil.attributes(usage: "incompatible")
        def incompatible2 = AttributeTestUtil.attributes(usage: "incompatible2")

        def transform1 = registration(fromSource, incompatible)
        def transform2 = registration(fromSource, incompatible2)

        def sourceVariant = variant([usage: "source"])
        def variants = [ sourceVariant ]

        allowEdge("usage", "source", "fromSource")
        allowEdge("usage", "compatible", "requested")

        given:
        transformRegistry.registrations >> [transform1, transform2]

        when:
        def result = transformations.findCandidateTransformationChains(variants, requested)

        then:
        result.empty
    }

    def "does not match on unrelated transform"() {
        def requested = AttributeTestUtil.attributes([usage: "hello"])

        def fromSource = AttributeTestUtil.attributes([other: "fromSource"])
        def compatible = AttributeTestUtil.attributes([other: "compatible"])

        def sourceVariant = variant([usage: "source"])
        def variants = [ sourceVariant ]

        def transform1 = registration(fromSource, compatible)

        allowEdge("usage", "source", "fromSource")
        allowEdge("usage", "compatible", "requested")

        given:
        transformRegistry.registrations >> [transform1]

        when:
        def result = transformations.findCandidateTransformationChains(variants, requested)

        then:
        // sourceVariant transformed by transform1 produces a variant with attributes incompatible with requested
        result.empty
    }

    private void assertTransformChain(TransformedVariant chain, ResolvedVariant source, AttributeContainer finalAttributes, TransformRegistration... registrations) {
        assert chain.root == source
        assert chain.attributes == finalAttributes
        def actualSteps = []
        chain.transformChain.visitTransformSteps {
            actualSteps << it
        }
        def expectedSteps = registrations*.transformStep
        assert actualSteps == expectedSteps
    }

    private TransformRegistration registration(AttributeContainer from, AttributeContainer to) {
        return Mock(TransformRegistration) {
            getFrom() >> from
            getTo() >> to
            getTransformStep() >> Stub(TransformStep)
        }
    }

    private ResolvedVariant variant(Map<String, Object> attributes) {
        return variant(AttributeTestUtil.attributes(attributes))
    }

    private ResolvedVariant variant(AttributeContainerInternal attributes) {
        return Mock(ResolvedVariant) {
            getAttributes() >> attributes
        }
    }

    static class CompatibleRule implements AttributeCompatibilityRule<String> {

        String from
        String to

        @Inject
        CompatibleRule(String from, String to) {
            this.from = from
            this.to = to
        }

        @Override
        void execute(CompatibilityCheckDetails<String> details) {
            if (to == details.consumerValue && from == details.producerValue) {
                details.compatible()
            }
        }
    }

    private <T> void allowEdge(String attr, String value1, String value2) {
        schema.attribute(Attribute.of(attr, String)) {
            it.compatibilityRules.add(CompatibleRule) {
                it.params(value1, value2)
            }
        }
    }

}
