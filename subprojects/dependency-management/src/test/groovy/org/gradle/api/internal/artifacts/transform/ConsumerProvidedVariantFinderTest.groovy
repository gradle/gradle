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

import org.gradle.api.Action
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.internal.artifacts.ArtifactTransformRegistration
import org.gradle.api.internal.artifacts.VariantTransformRegistry
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.internal.component.model.AttributeMatcher
import org.gradle.util.AttributeTestUtil
import spock.lang.Issue
import spock.lang.Specification

class ConsumerProvidedVariantFinderTest extends Specification {
    def attributeMatcher = Mock(AttributeMatcher)
    def transformRegistry = Mock(VariantTransformRegistry)

    def c1 = AttributeTestUtil.attributes(a1: "1", a2: 1)
    def c2 = AttributeTestUtil.attributes(a1: "1", a2: 2)
    def c3 = AttributeTestUtil.attributes(a1: "1", a2: 3)

    ConsumerProvidedVariantFinder transformations

    def setup() {
        def schema = Mock(AttributesSchemaInternal)
        schema.matcher() >> attributeMatcher
        transformations = new ConsumerProvidedVariantFinder(transformRegistry, schema, AttributeTestUtil.attributesFactory())
    }

    def "selects transform that can produce variant that is compatible with requested"() {
        def transform1 = registration(c1, c3)
        def transform2 = registration(c1, c2)
        def transform3 = registration(c2, c3)
        def requested = AttributeTestUtil.attributes([a1: "requested"])

        def sourceVariant = variant([a1: "source"])
        def otherVariant = variant([a1: "source2"])
        def variants = [ sourceVariant, otherVariant ]

        given:
        transformRegistry.transforms >> [transform1, transform2, transform3]

        when:
        def result = transformations.findTransformedVariants(variants, requested)

        then:
        result.size() == 1
        assertTransformChain(result.first(), sourceVariant, c2, transform2)

        and:
        1 * attributeMatcher.isMatching(c3, requested) >> false
        1 * attributeMatcher.isMatching(c2, requested) >> true
        1 * attributeMatcher.isMatching(sourceVariant.getAttributes(), c1) >> true
        1 * attributeMatcher.isMatching(otherVariant.getAttributes(), c1) >> false
        0 * attributeMatcher._
    }

    def "selects all transforms that can produce variant that is compatible with requested"() {
        def transform1 = registration(c1, c3)
        def transform2 = registration(c1, c2)
        def transform3 = registration(c2, c2)
        def transform4 = registration(c2, c3)
        def requested = AttributeTestUtil.attributes([a1: "requested"])

        def variants = [
            variant([a1: "source"]),
            variant([a1: "source2"])
        ]

        given:
        transformRegistry.transforms >> [transform1, transform2, transform3, transform4]

        when:
        def result = transformations.findTransformedVariants(variants, requested)

        then:
        result.size() == 4
        assertTransformChain(result[0], variants[0], c3, transform1)
        assertTransformChain(result[1], variants[0], c2, transform2)
        assertTransformChain(result[2], variants[1], c2, transform3)
        assertTransformChain(result[3], variants[1], c3, transform4)

        and:
        1 * attributeMatcher.isMatching(c3, requested) >> true
        1 * attributeMatcher.isMatching(c2, requested) >> true
        1 * attributeMatcher.isMatching(variants[0].getAttributes(), c1) >> true
        1 * attributeMatcher.isMatching(variants[0].getAttributes(), c2) >> false
        1 * attributeMatcher.isMatching(variants[1].getAttributes(), c1) >> false
        1 * attributeMatcher.isMatching(variants[1].getAttributes(), c2) >> true
        0 * attributeMatcher._
    }

    def "transform match is reused"() {
        def transform1 = registration(c1, c3)
        def transform2 = registration(c1, c2)
        def requested = AttributeTestUtil.attributes([a1: "requested"])
        def sourceVariant = variant([a1: "source"])
        def otherVariant = variant([a1: "source2"])
        def variants = [ sourceVariant, otherVariant ]

        given:
        transformRegistry.transforms >> [transform1, transform2]

        when:
        def result = transformations.findTransformedVariants(variants, requested)

        then:
        result.size() == 1
        assertTransformChain(result.first(), sourceVariant, c2, transform2)

        and:
        1 * attributeMatcher.isMatching(sourceVariant.getAttributes(), c1) >> true
        1 * attributeMatcher.isMatching(otherVariant.getAttributes(), c1) >> false
        1 * attributeMatcher.isMatching(c3, requested) >> false
        1 * attributeMatcher.isMatching(c2, requested) >> true
        0 * attributeMatcher._

        when:
        def anotherVariants = [
                variant(sourceVariant.getAttributes()),
                variant(otherVariant.getAttributes())
        ]
        def result2 = transformations.findTransformedVariants(anotherVariants, requested)

        then:
        result2.size() == 1
        assertTransformChain(result2.first(), anotherVariants[0], c2, transform2)

        and:
        0 * attributeMatcher._
    }

    def "selects chain of transforms that can produce variant that is compatible with requested"() {
        def c4 = AttributeTestUtil.attributes([a1: "4"])
        def c5 = AttributeTestUtil.attributes([a1: "5"])
        def c6 = AttributeTestUtil.attributes([a1: "6"])
        def c7 = AttributeTestUtil.attributes([a1: "7"])
        def c8 = AttributeTestUtil.attributes([a1: "8"])
        def c9 = AttributeTestUtil.attributes([a1: "9"])
        def c10 = AttributeTestUtil.attributes([a1: "10"])
        def requested = AttributeTestUtil.attributes([a1: "requested"])
        def variants = [
            variant([a1: "source"]),
            variant([a1: "source2"])
        ]
        def transform1 = registration(c1, c3)
        def transform2 = registration(c1, c2)
        def transform3 = registration(c4, c5)
        def transform4 = registration(c6, c7)
        def transform5 = registration(c8, c9)
        def transform6 = registration(c8, c10)

        given:
        transformRegistry.transforms >> [transform1, transform2, transform3, transform4, transform5, transform6]

        when:
        def result = transformations.findTransformedVariants(variants, requested)

        then:
        result.size() == 2
        assertTransformChain(result[0], variants[0], AttributeTestUtil.attributes([a1: "5", a2: 2]), transform2, transform3)
        assertTransformChain(result[1], variants[1], c9, transform4, transform5)

        and:
        1 * attributeMatcher.isMatching(variants[0].getAttributes(), c1) >> true
        1 * attributeMatcher.isMatching(variants[1].getAttributes(), c6) >> true
        1 * attributeMatcher.isMatching(c2, c4) >> true
        1 * attributeMatcher.isMatching(c7, c8) >> true
        1 * attributeMatcher.isMatching(c5, requested) >> true
        1 * attributeMatcher.isMatching(c9, requested) >> true
        _ * attributeMatcher.isMatching(_ ,_) >> false
        0 * attributeMatcher._
    }

    def "prefers direct transformation over indirect"() {
        def c4 = AttributeTestUtil.attributes([a1: "4"])
        def c5 = AttributeTestUtil.attributes([a1: "5"])
        def c6 = AttributeTestUtil.attributes([a1: "6"])
        def c7 = AttributeTestUtil.attributes([a1: "7"])
        def requested = AttributeTestUtil.attributes([a1: "requested"])
        def variants = [
            variant([a1: "source"]),
            variant([a1: "source2"])
        ]
        def transform1 = registration(c1, c3)
        def transform2 = registration(c1, c2)
        def transform3 = registration(c4, c5)
        def transform4 = registration(c6, c7)

        given:
        transformRegistry.transforms >> [transform1, transform2, transform3, transform4]

        when:
        def result = transformations.findTransformedVariants(variants, requested)

        then:
        result.size() == 2
        assertTransformChain(result[0], variants[0], c5, transform3)
        assertTransformChain(result[1], variants[1], c7, transform4)

        and:
        1 * attributeMatcher.isMatching(c2, requested) >> true
        1 * attributeMatcher.isMatching(c5, requested) >> true
        1 * attributeMatcher.isMatching(c7, requested) >> true
        1 * attributeMatcher.isMatching(variants[0].getAttributes(), c4) >> true
        1 * attributeMatcher.isMatching(variants[1].getAttributes(), c6) >> true
        0 * attributeMatcher.isMatching(c5, c1) >> true
        0 * attributeMatcher.isMatching(c7, c1) >> true
        _ * attributeMatcher.isMatching(_, _) >> false
        0 * attributeMatcher._
    }

    def "prefers shortest chain of transforms #registrationsIndex"() {
        def c4 = AttributeTestUtil.attributes([a1: "4"])
        def c5 = AttributeTestUtil.attributes([a1: "5"])
        def requested = AttributeTestUtil.attributes([a1: "requested"])
        def variants = [
            variant([a1: "source"])
        ]
        def transform1 = registration(c2, c3)
        def transform2 = registration(c2, c4)
        def transform3 = registration(c3, c4)
        def transform4 = registration(c4, c5)
        def registrations = [transform1, transform2, transform3, transform4]

        def requestedForReg4 = AttributeTestUtil.attributesFactory().concat(requested, c4)

        given:
        transformRegistry.transforms >> [registrations[registrationsIndex[0]], registrations[registrationsIndex[1]], registrations[registrationsIndex[2]], registrations[registrationsIndex[3]]]

        when:
        def result = transformations.findTransformedVariants(variants, requested)

        then:
        result.size() == 1
        assertTransformChain(result.first(), variants[0], c5, transform2, transform4)

        and:
        1 * attributeMatcher.isMatching(c3, requested) >> false
        1 * attributeMatcher.isMatching(c4, requested) >> false
        1 * attributeMatcher.isMatching(c5, requested) >> true
        1 * attributeMatcher.isMatching(variants[0].getAttributes(), c4) >> false
        1 * attributeMatcher.isMatching(c3, requestedForReg4) >> false
        1 * attributeMatcher.isMatching(c4, requestedForReg4) >> true
        1 * attributeMatcher.isMatching(variants[0].getAttributes(), c2) >> true
        1 * attributeMatcher.isMatching(variants[0].getAttributes(), c3) >> false
        0 * attributeMatcher._

        where:
        registrationsIndex << (0..3).permutations()
    }

    @Issue("gradle/gradle#7061")
    def "selects chain of transforms that only all the attributes are satisfied"() {
        def c4 = AttributeTestUtil.attributes([a1: "2", a2: 2])
        def c5 = AttributeTestUtil.attributes([a1: "2", a2: 3])
        def c6 = AttributeTestUtil.attributes([a1: "2"])
        def c7 = AttributeTestUtil.attributes([a1: "3"])
        def requested = AttributeTestUtil.attributes([a1: "3", a2: 3])
        def variants = [
            variant([a1: "1", a2: 1])
        ]
        def transform1 = registration(c1, c4)
        def transform2 = registration(c1, c5)
        def transform3 = registration(c6, c7)

        given:
        transformRegistry.transforms >> [transform1, transform2, transform3]

        when:
        def result = transformations.findTransformedVariants(variants, requested)

        then:
        result.size() == 1
        assertTransformChain(result.first(), variants[0], requested, transform2, transform3)

        and:
        1 * attributeMatcher.isMatching(c4, requested) >> false
        1 * attributeMatcher.isMatching(c5, requested) >> false
        1 * attributeMatcher.isMatching(c7, requested) >> true
        1 * attributeMatcher.isMatching(variants[0].getAttributes(), c6) >> false
        1 * attributeMatcher.isMatching(c4, c5) >> false // "2" 3 ; c5
        1 * attributeMatcher.isMatching(c5, c5) >> true
        1 * attributeMatcher.isMatching(variants[0].getAttributes(), c1) >> true
        0 * attributeMatcher._
    }

    def "returns empty list when no transforms are available to produce requested variant"() {
        def transform1 = registration(c1, c3)
        def transform2 = registration(c1, c2)
        def requested = AttributeTestUtil.attributes([a1: "requested"])
        def variants = [
            variant([a1: "source"])
        ]

        given:
        transformRegistry.transforms >> [transform1, transform2]

        when:
        def result = transformations.findTransformedVariants(variants, requested)

        then:
        result.empty

        and:
        1 * attributeMatcher.isMatching(c3, requested) >> false
        1 * attributeMatcher.isMatching(c2, requested) >> false
        0 * attributeMatcher._
    }

    def "caches negative match"() {
        def transform1 = registration(c1, c3)
        def transform2 = registration(c1, c2)
        def requested = AttributeTestUtil.attributes([a1: "requested"])
        def variants = [
            variant([a1: "source"])
        ]

        given:
        transformRegistry.transforms >> [transform1, transform2]

        when:
        def result = transformations.findTransformedVariants(variants, requested)

        then:
        result.empty

        and:
        1 * attributeMatcher.isMatching(c3, requested) >> false
        1 * attributeMatcher.isMatching(c2, requested) >> false
        0 * attributeMatcher._

        when:
        def result2 = transformations.findTransformedVariants(variants, requested)

        then:
        result2.empty

        and:
        0 * attributeMatcher._
    }

    def "does not match on unrelated transform"() {
        def from = AttributeTestUtil.attributes([a2: 1])
        def to = AttributeTestUtil.attributes([a2: 42])

        def variants = [
            variant([a1: "source"])
        ]
        def requested = AttributeTestUtil.attributes([a1: "hello"])

        def transform1 = registration(from, to)

        def concatTo = AttributeTestUtil.attributesFactory().concat(variants[0].getAttributes().asImmutable(), to)

        given:
        transformRegistry.transforms >> [transform1]

        when:
        def result = transformations.findTransformedVariants(variants, requested)

        then:
        result.empty

        and:
        1 * attributeMatcher.isMatching(to, requested) >> true
        1 * attributeMatcher.isMatching(variants[0].getAttributes(), from) >> true
        1 * attributeMatcher.isMatching(concatTo, requested) >> false
        0 * attributeMatcher._
    }

    private void assertTransformChain(TransformedVariant chain, ResolvedVariant source, AttributeContainer finalAttributes, ArtifactTransformRegistration... steps) {
        assert chain.root == source
        assert chain.attributes == finalAttributes
        assert chain.transformation.stepsCount() == steps.length
        def actualSteps = []
        chain.transformation.visitTransformationSteps {
            actualSteps << it
        }
        def expectedSteps = steps*.transformationStep
        assert actualSteps == expectedSteps
    }

    private ArtifactTransformRegistration registration(AttributeContainer from, AttributeContainer to) {
        def transformationStep = Stub(TransformationStep)
        _ * transformationStep.visitTransformationSteps(_) >> { Action action -> action.execute(transformationStep) }
        _ * transformationStep.stepsCount() >> 1

        return Mock(ArtifactTransformRegistration) {
            getFrom() >> from
            getTo() >> to
            getTransformationStep() >> transformationStep
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
