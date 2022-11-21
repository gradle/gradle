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
    def matcher = Mock(AttributeMatcher)
    def transformRegistrations = Mock(VariantTransformRegistry)

    def c1 = AttributeTestUtil.attributes(a1: "1", a2: 1)
    def c2 = AttributeTestUtil.attributes(a1: "1", a2: 2)
    def c3 = AttributeTestUtil.attributes(a1: "1", a2: 3)

    ConsumerProvidedVariantFinder matchingCache
    def setup() {
        def schema = Mock(AttributesSchemaInternal)
        schema.matcher() >> matcher
        matchingCache = new ConsumerProvidedVariantFinder(transformRegistrations, schema, AttributeTestUtil.attributesFactory())
    }

    def "selects transform that can produce variant that is compatible with requested"() {
        def reg1 = registration(c1, c3)
        def reg2 = registration(c1, c2)
        def reg3 = registration(c2, c3)
        def requested = AttributeTestUtil.attributes([a1: "requested"])
        def variants = [
            variant([a1: "source"]),
            variant([a1: "source2"])
        ]

        given:
        transformRegistrations.transforms >> [reg1, reg2, reg3]

        when:
        def result = matchingCache.findTransformedVariants(variants, requested)

        then:
        result.size() == 1
        result.first().attributes == c2
        result.first().transformation.is(reg2.transformationStep)

        and:
        1 * matcher.isMatching(c3, requested) >> false
        1 * matcher.isMatching(c2, requested) >> true
        1 * matcher.isMatching(variants[0].getAttributes(), c1) >> true
        1 * matcher.isMatching(variants[1].getAttributes(), c1) >> false
        0 * matcher._
    }

    def "selects all transforms that can produce variant that is compatible with requested"() {
        def reg1 = registration(c1, c3)
        def reg2 = registration(c1, c2)
        def reg3 = registration(c2, c2)
        def reg4 = registration(c2, c3)
        def requested = AttributeTestUtil.attributes([a1: "requested"])
        def variants = [
            variant([a1: "source"]),
            variant([a1: "source2"])
        ]

        given:
        transformRegistrations.transforms >> [reg1, reg2, reg3, reg4]

        when:
        def result = matchingCache.findTransformedVariants(variants, requested)

        then:
        result.size() == 4
        result*.root == [variants[0], variants[0], variants[1], variants[1]]
        result*.attributes == [c3, c2, c2, c3]
        result*.transformation == [reg1.transformationStep, reg2.transformationStep, reg3.transformationStep, reg4.transformationStep]

        and:
        1 * matcher.isMatching(c3, requested) >> true
        1 * matcher.isMatching(c2, requested) >> true
        1 * matcher.isMatching(variants[0].getAttributes(), c1) >> true
        1 * matcher.isMatching(variants[0].getAttributes(), c2) >> false
        1 * matcher.isMatching(variants[1].getAttributes(), c1) >> false
        1 * matcher.isMatching(variants[1].getAttributes(), c2) >> true
        0 * matcher._
    }

    def "transform match is reused"() {
        def reg1 = registration(c1, c3)
        def reg2 = registration(c1, c2)
        def requested = AttributeTestUtil.attributes([a1: "requested"])
        def variants = [
            variant([a1: "source"]),
            variant([a2: "source2"])
        ]
        def anotherVariants = [
            variant(variants[0].getAttributes()),
            variant(variants[1].getAttributes())
        ]

        given:
        transformRegistrations.transforms >> [reg1, reg2]

        when:
        def result = matchingCache.findTransformedVariants(variants, requested)

        then:
        result.size() == 1
        def match = result.first()
        match.transformation.is(reg2.transformationStep)

        and:
        1 * matcher.isMatching(variants[0].getAttributes(), c1) >> true
        1 * matcher.isMatching(variants[1].getAttributes(), c1) >> false
        1 * matcher.isMatching(c3, requested) >> false
        1 * matcher.isMatching(c2, requested) >> true
        0 * matcher._

        when:
        def result2 = matchingCache.findTransformedVariants(anotherVariants, requested)

        then:
        result2.size() == 1
        def match2 = result2.first()
        match2.attributes.is(match.attributes)
        match2.transformation.is(match.transformation)

        and:
        0 * matcher._
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
        def reg1 = registration(c1, c3)
        def reg2 = registration(c1, c2)
        def reg3 = registration(c4, c5)
        def reg4 = registration(c6, c7)
        def reg5 = registration(c8, c9)
        def reg6 = registration(c8, c10)

        given:
        transformRegistrations.transforms >> [reg1, reg2, reg3, reg4, reg5, reg6]

        when:
        def result = matchingCache.findTransformedVariants(variants, requested)

        then:
        result.size() == 2
        result*.root == [variants[0], variants[1]]
        getSteps(result.get(0)) == [reg2.transformationStep, reg3.transformationStep]
        getSteps(result.get(1)) == [reg4.transformationStep, reg5.transformationStep]

        and:
        1 * matcher.isMatching(variants[0].getAttributes(), c1) >> true
        1 * matcher.isMatching(variants[1].getAttributes(), c6) >> true
        1 * matcher.isMatching(c2, c4) >> true
        1 * matcher.isMatching(c7, c8) >> true
        1 * matcher.isMatching(c5, requested) >> true
        1 * matcher.isMatching(c9, requested) >> true
        _ * matcher.isMatching(_ ,_) >> false
        0 * matcher._
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
        def reg1 = registration(c1, c3)
        def reg2 = registration(c1, c2)
        def reg3 = registration(c4, c5)
        def reg4 = registration(c6, c7)

        given:
        transformRegistrations.transforms >> [reg1, reg2, reg3, reg4]

        when:
        def result = matchingCache.findTransformedVariants(variants, requested)

        then:
        result.size() == 2
        result.get(0).transformation.is(reg3.transformationStep)
        result.get(1).transformation.is(reg4.transformationStep)

        and:
        1 * matcher.isMatching(c2, requested) >> true
        1 * matcher.isMatching(c5, requested) >> true
        1 * matcher.isMatching(c7, requested) >> true
        1 * matcher.isMatching(variants[0].getAttributes(), c4) >> true
        1 * matcher.isMatching(variants[1].getAttributes(), c6) >> true
        0 * matcher.isMatching(c5, c1) >> true
        0 * matcher.isMatching(c7, c1) >> true
        _ * matcher.isMatching(_, _) >> false
        0 * matcher._
    }

    def "prefers shortest chain of transforms #registrationsIndex"() {
        def c4 = AttributeTestUtil.attributes([a1: "4"])
        def c5 = AttributeTestUtil.attributes([a1: "5"])
        def requested = AttributeTestUtil.attributes([a1: "requested"])
        def variants = [
            variant([a1: "source"])
        ]
        def reg1 = registration(c2, c3)
        def reg2 = registration(c2, c4)
        def reg3 = registration(c3, c4)
        def reg4 = registration(c4, c5)
        def registrations = [reg1, reg2, reg3, reg4]

        def requestedForReg4 = AttributeTestUtil.attributesFactory().concat(requested, c4)

        given:
        transformRegistrations.transforms >> [registrations[registrationsIndex[0]], registrations[registrationsIndex[1]], registrations[registrationsIndex[2]], registrations[registrationsIndex[3]]]

        when:
        def result = matchingCache.findTransformedVariants(variants, requested)

        then:
        result.size() == 1
        getSteps(result.first()) == [reg2.transformationStep, reg4.transformationStep]

        and:
        1 * matcher.isMatching(c3, requested) >> false
        1 * matcher.isMatching(c4, requested) >> false
        1 * matcher.isMatching(c5, requested) >> true
        1 * matcher.isMatching(variants[0].getAttributes(), c4) >> false
        1 * matcher.isMatching(c3, requestedForReg4) >> false
        1 * matcher.isMatching(c4, requestedForReg4) >> true
        1 * matcher.isMatching(variants[0].getAttributes(), c2) >> true
        1 * matcher.isMatching(variants[0].getAttributes(), c3) >> false
        0 * matcher._

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
        def reg1 = registration(c1, c4)
        def reg2 = registration(c1, c5)
        def reg3 = registration(c6, c7)

        given:
        transformRegistrations.transforms >> [reg1, reg2, reg3]

        when:
        def result = matchingCache.findTransformedVariants(variants, requested)

        then:
        result.size() == 1

        and:
        1 * matcher.isMatching(c4, requested) >> false
        1 * matcher.isMatching(c5, requested) >> false
        1 * matcher.isMatching(c7, requested) >> true
        1 * matcher.isMatching(variants[0].getAttributes(), c6) >> false
        1 * matcher.isMatching(c4, c5) >> false // "2" 3 ; c5
        1 * matcher.isMatching(c5, c5) >> true
        1 * matcher.isMatching(variants[0].getAttributes(), c1) >> true
        0 * matcher._
    }

    def "returns empty list when no transforms are available to produce requested variant"() {
        def reg1 = registration(c1, c3)
        def reg2 = registration(c1, c2)
        def requested = AttributeTestUtil.attributes([a1: "requested"])
        def variants = [
            variant([a1: "source"])
        ]

        given:
        transformRegistrations.transforms >> [reg1, reg2]

        when:
        def result = matchingCache.findTransformedVariants(variants, requested)

        then:
        result.empty

        and:
        1 * matcher.isMatching(c3, requested) >> false
        1 * matcher.isMatching(c2, requested) >> false
        0 * matcher._
    }

    def "caches negative match"() {
        def reg1 = registration(c1, c3)
        def reg2 = registration(c1, c2)
        def requested = AttributeTestUtil.attributes([a1: "requested"])
        def variants = [
            variant([a1: "source"])
        ]

        given:
        transformRegistrations.transforms >> [reg1, reg2]

        when:
        def result = matchingCache.findTransformedVariants(variants, requested)

        then:
        result.empty

        and:
        1 * matcher.isMatching(c3, requested) >> false
        1 * matcher.isMatching(c2, requested) >> false
        0 * matcher._

        when:
        def result2 = matchingCache.findTransformedVariants(variants, requested)

        then:
        result2.empty

        and:
        0 * matcher._
    }

    def "does not match on unrelated transform"() {
        def from = AttributeTestUtil.attributes([a2: 1])
        def to = AttributeTestUtil.attributes([a2: 42])

        def variants = [
            variant([a1: "source"])
        ]
        def requested = AttributeTestUtil.attributes([a1: "hello"])

        def reg1 = registration(from, to)

        def concatTo = AttributeTestUtil.attributesFactory().concat(variants[0].getAttributes().asImmutable(), to)

        given:
        transformRegistrations.transforms >> [reg1]

        when:
        def result = matchingCache.findTransformedVariants(variants, requested)

        then:
        result.empty

        and:
        1 * matcher.isMatching(to, requested) >> true
        1 * matcher.isMatching(variants[0].getAttributes(), from) >> true
        1 * matcher.isMatching(concatTo, requested) >> false
        0 * matcher._
    }

    List<TransformationStep> getSteps(TransformedVariant result) {
        def steps = [] as List<TransformationStep>
        result.transformation.visitTransformationSteps(steps::add)
        steps
    }

    private ArtifactTransformRegistration registration(AttributeContainer from, AttributeContainer to) {
        def transformationStep = Stub(TransformationStep)
        _ * transformationStep.visitTransformationSteps(_) >> { Action action -> action.execute(transformationStep) }

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
