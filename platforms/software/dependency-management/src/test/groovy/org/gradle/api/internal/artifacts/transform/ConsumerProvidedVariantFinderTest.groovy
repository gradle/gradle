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

import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.internal.artifacts.TransformRegistration
import org.gradle.api.internal.artifacts.VariantTransformRegistry
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.AttributeSchemaServices
import org.gradle.api.internal.attributes.matching.AttributeMatcher
import org.gradle.util.AttributeTestUtil
import spock.lang.Issue
import spock.lang.Specification

class ConsumerProvidedVariantFinderTest extends Specification {
    def attributeMatcher = Mock(AttributeMatcher)
    def transformRegistry = Mock(VariantTransformRegistry)
    def services = Mock(AttributeSchemaServices) {
        getMatcher(_, _) >> attributeMatcher
        getSchemaFactory() >> AttributeTestUtil.services().getSchemaFactory()
    }

    ConsumerProvidedVariantFinder transformations = new ConsumerProvidedVariantFinder(
        transformRegistry,
        AttributeTestUtil.mutableSchema(),
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

        given:
        transformRegistry.registrations >> [transform1, transform2, transform3]

        when:
        def result = transformations.findTransformedVariants(variants, requested)

        then:
        result.size() == 1
        assertTransformChain(result.first(), sourceVariant, compatible, transform2)

        and:
        // sourceVariant can be transformed by a transform starting with fromSource attributes
        attributeMatcher.isMatchingCandidate(sourceVariant.getAttributes(), fromSource) >> true
        // otherVariant cannot be transformed by a transform starting with fromSource attributes
        attributeMatcher.isMatchingCandidate(otherVariant.getAttributes(), fromSource) >> false
        // incompatible attributes are not compatible with requested
        attributeMatcher.isMatchingCandidate(incompatible, requested) >> false
        // compatible attributes are compatible with requested
        attributeMatcher.isMatchingCandidate(compatible, requested) >> true

        0 * attributeMatcher._
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

        given:
        transformRegistry.registrations >> [transform1, transform2, transform3, transform4]

        when:
        def result = transformations.findTransformedVariants(variants, requested)

        then:
        result.size() == 4
        assertTransformChain(result[0], sourceVariant, compatible, transform1)
        assertTransformChain(result[1], sourceVariant, compatible2, transform2)
        assertTransformChain(result[2], otherVariant, compatible2, transform3)
        assertTransformChain(result[3], otherVariant, compatible, transform4)

        and:
        // source variant matches fromSource, but not fromOther
        attributeMatcher.isMatchingCandidate(sourceVariant.getAttributes(), fromSource) >> true
        attributeMatcher.isMatchingCandidate(sourceVariant.getAttributes(), fromOther) >> false
        // other variant matches fromOther, but not fromSource
        attributeMatcher.isMatchingCandidate(otherVariant.getAttributes(), fromSource) >> false
        attributeMatcher.isMatchingCandidate(otherVariant.getAttributes(), fromOther) >> true

        // compatible and compatible2 are compatible with requested attributes
        attributeMatcher.isMatchingCandidate(compatible, requested) >> true
        attributeMatcher.isMatchingCandidate(compatible2, requested) >> true

        0 * attributeMatcher._
    }

    def "transform match is reused"() {
        def requested = AttributeTestUtil.attributes([usage: "requested"])

        def fromSource = AttributeTestUtil.attributes(usage: "fromSource")
        def compatible = AttributeTestUtil.attributes(usage: "compatible")
        def incompatible = AttributeTestUtil.attributes(usage: "incompatible")

        def transform1 = registration(fromSource, incompatible)
        def transform2 = registration(fromSource, compatible)

        def sourceVariant = variant([usage: "source"])
        def otherVariant = variant([usage: "other"])
        def variants = [ sourceVariant, otherVariant ]

        given:
        transformRegistry.registrations >> [transform1, transform2]

        when:
        def result = transformations.findTransformedVariants(variants, requested)

        then:
        result.size() == 1
        assertTransformChain(result.first(), sourceVariant, compatible, transform2)

        and:
        // source variant matches fromSource, other variant does not
        attributeMatcher.isMatchingCandidate(sourceVariant.getAttributes(), fromSource) >> true
        attributeMatcher.isMatchingCandidate(otherVariant.getAttributes(), fromSource) >> false

        // incompatible is not compatible with requested
        attributeMatcher.isMatchingCandidate(incompatible, requested) >> false
        // compatible is compatible with requested
        attributeMatcher.isMatchingCandidate(compatible, requested) >> true
        0 * attributeMatcher._

        when:
        def anotherVariants = [
                variant(otherVariant.getAttributes()),
                variant(sourceVariant.getAttributes())
        ]
        def result2 = transformations.findTransformedVariants(anotherVariants, requested)

        then:
        result2.size() == 1
        assertTransformChain(result2.first(), anotherVariants[1], compatible, transform2)

        and:
        // source variant matches fromSource, other variant does not
        attributeMatcher.isMatchingCandidate(sourceVariant.getAttributes(), fromSource) >> true
        attributeMatcher.isMatchingCandidate(otherVariant.getAttributes(), fromSource) >> false

        // incompatible is not compatible with requested
        attributeMatcher.isMatchingCandidate(incompatible, requested) >> false
        // compatible is compatible with requested
        attributeMatcher.isMatchingCandidate(compatible, requested) >> true
        0 * attributeMatcher._
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

        given:
        transformRegistry.registrations >> [transform1, transform2, transform3, transform4, transform5, transform6]

        when:
        def result = transformations.findTransformedVariants(variants, requested)

        then:
        result.size() == 2
        // sourceVariant + transform2 + transform3 = compatible
        assertTransformChain(result[0], sourceVariant, compatible, transform2, transform3)
        // otherVariant + transform2 + transform4 = compatible2
        assertTransformChain(result[1], otherVariant, compatible2, transform4, transform5)

        and:
        // source variant matches fromSource, other variant matches fromOther
        attributeMatcher.isMatchingCandidate(sourceVariant.getAttributes(), fromSource) >> true
        attributeMatcher.isMatchingCandidate(otherVariant.getAttributes(), fromOther) >> true

        // intermediate matches fromIntermediate and intermediate2 matches fromIntermediate2
        // this lets us build the chain from one transform to the next
        attributeMatcher.isMatchingCandidate(intermediate, fromIntermediate) >> true
        attributeMatcher.isMatchingCandidate(intermediate2, fromIntermediate2) >> true

        // compatible and compatible2 are compatible with requested attributes
        attributeMatcher.isMatchingCandidate(compatible, requested) >> true
        attributeMatcher.isMatchingCandidate(compatible2, requested) >> true

        // all other matching attempts are not compatible
        _ * attributeMatcher.isMatchingCandidate(_ ,_) >> false
        0 * attributeMatcher._
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

        given:
        transformRegistry.registrations >> [transform1, transform2, transform3, transform4]

        when:
        def result = transformations.findTransformedVariants(variants, requested)

        then:
        result.size() == 2
        assertTransformChain(result[0], sourceVariant, compatible, transform3)
        assertTransformChain(result[1], otherVariant, compatible2, transform4)
        // possible longer chains
        // (sourceVariant:fromSource) + transform3 -> (compatible:fromIndirect) + transform2 -> (compatibleIndirect:requested)
        // (otherVariant:fromOther) + transform4 -> (compatible2:fromIndirect) + transform2 -> (compatibleIndirect:requested)

        and:
        // source variant matches fromSource, other variant matches fromOther
        attributeMatcher.isMatchingCandidate(sourceVariant.getAttributes(), fromSource) >> true
        attributeMatcher.isMatchingCandidate(otherVariant.getAttributes(), fromOther) >> true

        // We should not attempt to compare compatible/compatible2 with fromIndirect because we should not attempt to make this chain
        0 * attributeMatcher.isMatchingCandidate(compatible, fromIndirect) >> true
        0 * attributeMatcher.isMatchingCandidate(compatible2, fromIndirect) >> true

        // compatible, compatible2 and compatibleIndirect are all compatible with requested attributes
        attributeMatcher.isMatchingCandidate(compatibleIndirect, requested) >> true
        attributeMatcher.isMatchingCandidate(compatible, requested) >> true
        attributeMatcher.isMatchingCandidate(compatible2, requested) >> true

        // all other matching attempts are not compatible
        _ * attributeMatcher.isMatchingCandidate(_, _) >> false
        0 * attributeMatcher._
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

        def fromIntermediate = AttributeTestUtil.attributesFactory().concat(requested, intermediate)

        given:
        transformRegistry.registrations >> [registrations[registrationsIndex[0]], registrations[registrationsIndex[1]], registrations[registrationsIndex[2]], registrations[registrationsIndex[3]]]

        when:
        def result = transformations.findTransformedVariants(variants, requested)

        then:
        result.size() == 1
        assertTransformChain(result.first(), sourceVariant, compatible, transform2, transform4)

        and:
        // source variant matches fromSource, but not fromOther
        attributeMatcher.isMatchingCandidate(sourceVariant.getAttributes(), fromSource) >> true
        attributeMatcher.isMatchingCandidate(sourceVariant.getAttributes(), fromOther) >> false

        // fromOther, intermediate and source variant are incompatible with each other
        attributeMatcher.isMatchingCandidate(sourceVariant.getAttributes(), intermediate) >> false
        attributeMatcher.isMatchingCandidate(fromOther, fromIntermediate) >> false
        attributeMatcher.isMatchingCandidate(intermediate, fromIntermediate) >> true

        // fromOther and intermediate are not acceptable matches for requested attributes
        attributeMatcher.isMatchingCandidate(fromOther, requested) >> false
        attributeMatcher.isMatchingCandidate(intermediate, requested) >> false
        // compatible is compatible with requested attributes
        attributeMatcher.isMatchingCandidate(compatible, requested) >> true

        0 * attributeMatcher._

        where:
        registrationsIndex << (0..3).permutations()
    }

    @Issue("gradle/gradle#7061")
    def "selects chain of transforms that only all the attributes are satisfied"() {
        def requested = AttributeTestUtil.attributes([usage: "requested", other: "transform3"])

        def fromSource = AttributeTestUtil.attributes(usage: "fromSource", other: "fromSource")
        def fromIntermediate = AttributeTestUtil.attributes([usage: "fromIntermediate"])
        def partialTransformed = AttributeTestUtil.attributes([usage: "fromIntermediate", other: "transform3"])

        def incompatible = AttributeTestUtil.attributes([usage: "incompatible"])
        def intermediate = AttributeTestUtil.attributes([usage: "intermediate", other: "transform2"])
        def compatible = AttributeTestUtil.attributes([usage: "compatible", other: "transform3"])

        def sourceVariant = variant([usage: "source"])
        def variants = [ sourceVariant ]

        def transform1 = registration(fromSource, incompatible)
        def transform2 = registration(fromSource, intermediate)
        def transform3 = registration(fromIntermediate, compatible)

        given:
        transformRegistry.registrations >> [transform1, transform2, transform3]

        when:
        def result = transformations.findTransformedVariants(variants, requested)

        then:
        result.size() == 1
        assertTransformChain(result.first(), sourceVariant, compatible, transform2, transform3)

        and:
        // source variant matches fromSource, but not fromIntermediate
        attributeMatcher.isMatchingCandidate(sourceVariant.getAttributes(), fromIntermediate) >> false
        attributeMatcher.isMatchingCandidate(sourceVariant.getAttributes(), fromSource) >> true

        // partialTransformed is compatible with intermediate
        attributeMatcher.isMatchingCandidate(incompatible, partialTransformed) >> false
        attributeMatcher.isMatchingCandidate(intermediate, partialTransformed) >> true

        // compatible is compatible with requested attributes, but incompatible and intermediate are not
        attributeMatcher.isMatchingCandidate(incompatible, requested) >> false
        attributeMatcher.isMatchingCandidate(intermediate, requested) >> false
        attributeMatcher.isMatchingCandidate(compatible, requested) >> true

        0 * attributeMatcher._
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

        given:
        transformRegistry.registrations >> [transform1, transform2]

        when:
        def result = transformations.findTransformedVariants(variants, requested)

        then:
        result.empty

        and:
        // incompatible and incompatible2 are not compatible with requested attributes
        attributeMatcher.isMatchingCandidate(incompatible, requested) >> false
        attributeMatcher.isMatchingCandidate(incompatible2, requested) >> false
        0 * attributeMatcher._
    }

    def "caches negative match"() {
        def requested = AttributeTestUtil.attributes([usage: "requested"])

        def fromSource = AttributeTestUtil.attributes(usage: "fromSource")
        def incompatible = AttributeTestUtil.attributes(usage: "incompatible")
        def incompatible2 = AttributeTestUtil.attributes(usage: "incompatible2")

        def transform1 = registration(fromSource, incompatible2)
        def transform2 = registration(fromSource, incompatible)

        def sourceVariant = variant([usage: "source"])
        def variants = [ sourceVariant ]

        given:
        transformRegistry.registrations >> [transform1, transform2]

        when:
        def result = transformations.findTransformedVariants(variants, requested)

        then:
        result.empty

        and:
        // incompatible and incompatible2 are not compatible with requested attributes
        attributeMatcher.isMatchingCandidate(incompatible2, requested) >> false
        attributeMatcher.isMatchingCandidate(incompatible, requested) >> false
        0 * attributeMatcher._

        when:
        def result2 = transformations.findTransformedVariants(variants, requested)

        then:
        result2.empty

        and:
        0 * attributeMatcher._
    }

    def "does not match on unrelated transform"() {
        def requested = AttributeTestUtil.attributes([usage: "hello"])

        def fromSource = AttributeTestUtil.attributes([other: "fromSource"])
        def compatible = AttributeTestUtil.attributes([other: "compatible"])

        def sourceVariant = variant([usage: "source"])
        def variants = [ sourceVariant ]

        def transform1 = registration(fromSource, compatible)

        def finalAttributes = AttributeTestUtil.attributesFactory().concat(sourceVariant.getAttributes().asImmutable(), compatible)

        given:
        transformRegistry.registrations >> [transform1]

        when:
        def result = transformations.findTransformedVariants(variants, requested)

        then:
        // sourceVariant transformed by transform1 produces a variant with attributes incompatible with requested
        result.empty

        and:
        // source variant matches fromSource
        attributeMatcher.isMatchingCandidate(sourceVariant.getAttributes(), fromSource) >> true
        // compatible is compatible with requested attributes
        attributeMatcher.isMatchingCandidate(compatible, requested) >> true
        // attributes that are the result of the transform are not compatible with the request
        attributeMatcher.isMatchingCandidate(finalAttributes, requested) >> false

        0 * attributeMatcher._
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
}
