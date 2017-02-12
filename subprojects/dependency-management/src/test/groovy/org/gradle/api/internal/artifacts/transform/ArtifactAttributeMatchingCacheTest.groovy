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

import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.transform.ArtifactTransform
import org.gradle.api.artifacts.transform.ArtifactTransformTargets
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.internal.attributes.DefaultMutableAttributeContainer
import org.gradle.api.internal.attributes.DefaultAttributesSchema
import org.gradle.api.internal.attributes.DefaultImmutableAttributesFactory
import org.gradle.internal.component.model.ComponentAttributeMatcher
import spock.lang.Specification

class ArtifactAttributeMatchingCacheTest extends Specification {

    def matcher = Mock(ComponentAttributeMatcher)
    def schema = new DefaultAttributesSchema(matcher)
    def immutableAttributesFactory = new DefaultImmutableAttributesFactory()
    def transformRegistrations = Mock(ArtifactTransformRegistrationsInternal)
    def matchingCache = new ArtifactAttributeMatchingCache(transformRegistrations, schema)

    def a1 = Attribute.of("a1", String)
    def a2 = Attribute.of("a2", Integer)
    def c1 = attributes().attribute(a1, "1").attribute(a2, 1).asImmutable()
    def c2 = attributes().attribute(a1, "1").attribute(a2, 2).asImmutable()
    def c3 = attributes().attribute(a1, "1").attribute(a2, 3).asImmutable()

    static class Transform extends ArtifactTransform {
        void configure(AttributeContainer from, ArtifactTransformTargets targetRegistry) {
            def a1 = Attribute.of("a1", String)
            def a2 = Attribute.of("a2", Integer)

            from.attribute(a1, "1").attribute(a2, 1)
            targetRegistry.newTarget().attribute(a1, "1").attribute(a2, 2)
            targetRegistry.newTarget().attribute(a1, "1").attribute(a2, 3)
        }

        List<File> transform(File input, AttributeContainer target) {}
    }

    def "artifact is matched using matcher"() {
        when:
        matchingCache.areMatchingAttributes(c1, c1)
        matchingCache.areMatchingAttributes(c1, c2)

        then:
        1 * matcher.isMatching(schema, c1, c1, false)
        1 * matcher.isMatching(schema, c1, c2, false)
        0 * matcher._
    }

    def "artifact match is reused"() {
        given:
        matchingCache.areMatchingAttributes(c1, c1)

        when:
        matchingCache.areMatchingAttributes(c1, c1)

        then:
        0 * matcher._
    }

    def "selects first transform that can produce variant that is compatible with requested"() {
        def reg1 = new ArtifactTransformRegistration(c1, c3, Transform, {})
        def reg2 = new ArtifactTransformRegistration(c1, c2, Transform, {})
        def reg3 = new ArtifactTransformRegistration(c2, c3, Transform, {})
        def requested = attributes().attribute(a1, "requested")
        def source = attributes().attribute(a1, "source")

        given:
        transformRegistrations.transforms >> [reg1, reg2, reg3]

        when:
        def result = matchingCache.getTransform(source, requested)

        then:
        result.is(reg2.transform)

        and:
        1 * matcher.isMatching(schema, c1, source, true) >> true
        1 * matcher.isMatching(schema, c3, requested, true) >> false
        1 * matcher.isMatching(schema, c2, requested, true) >> true
        0 * matcher._
    }

    def "transform match is reused"() {
        def reg1 = new ArtifactTransformRegistration(c1, c3, Transform, {})
        def reg2 = new ArtifactTransformRegistration(c1, c2, Transform, {})
        def requested = attributes().attribute(a1, "requested")
        def source = attributes().attribute(a1, "source")

        given:
        transformRegistrations.transforms >> [reg1, reg2]

        when:
        def result = matchingCache.getTransform(source, requested)

        then:
        result.is(reg2.transform)

        and:
        1 * matcher.isMatching(schema, c1, source, true) >> true
        1 * matcher.isMatching(schema, c3, requested, true) >> false
        1 * matcher.isMatching(schema, c2, requested, true) >> true
        0 * matcher._

        when:
        def result2 = matchingCache.getTransform(source, requested)

        then:
        result2.is(result)

        and:
        0 * matcher._
    }

    def "returns null transformer when none is available to produce requested variant"() {
        def reg1 = new ArtifactTransformRegistration(c1, c3, Transform, {})
        def reg2 = new ArtifactTransformRegistration(c1, c2, Transform, {})
        def requested = attributes().attribute(a1, "requested")
        def source = attributes().attribute(a1, "source")

        given:
        transformRegistrations.transforms >> [reg1, reg2]

        when:
        def result = matchingCache.getTransform(source, requested)

        then:
        result == null

        and:
        1 * matcher.isMatching(schema, c1, source, true) >> true
        1 * matcher.isMatching(schema, c3, requested, true) >> false
        1 * matcher.isMatching(schema, c2, requested, true) >> false
        0 * matcher._
    }

    def "caches negative match"() {
        def reg1 = new ArtifactTransformRegistration(c1, c3, Transform, {})
        def reg2 = new ArtifactTransformRegistration(c1, c2, Transform, {})
        def requested = attributes().attribute(a1, "requested")
        def source = attributes().attribute(a1, "source")

        given:
        transformRegistrations.transforms >> [reg1, reg2]

        when:
        def result = matchingCache.getTransform(source, requested)

        then:
        result == null

        and:
        1 * matcher.isMatching(schema, c1, source, true) >> false
        0 * matcher._

        when:
        def result2 = matchingCache.getTransform(source, requested)

        then:
        result2 == null

        and:
        0 * matcher._
    }

    def "transformed artifact match is reused"() {
        given:
        def original = Mock(ResolvedArtifact)
        def result = Mock(ResolvedArtifact)

        when:
        matchingCache.putTransformedArtifact(original , c2, [result])

        then:
        matchingCache.getTransformedArtifacts(original, c2) == [result]
        0 * matcher._
    }

    def "empty attributes match"() {
        given:
        matcher.isMatching(_, _, _) >> false

        expect:
        matchingCache.areMatchingAttributes(attributes(), attributes())
        !matchingCache.areMatchingAttributes(attributes(), attributes().attribute(a1, "value"))
        !matchingCache.areMatchingAttributes(attributes().attribute(a1, "value"), attributes())
    }

    private DefaultMutableAttributeContainer attributes() {
        new DefaultMutableAttributeContainer(immutableAttributesFactory)
    }

}
