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

import junit.framework.AssertionFailedError
import org.gradle.api.Transformer
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.transform.ArtifactTransform
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.HasAttributes
import org.gradle.api.internal.attributes.DefaultAttributesSchema
import org.gradle.api.internal.attributes.DefaultImmutableAttributesFactory
import org.gradle.api.internal.attributes.DefaultMutableAttributeContainer
import org.gradle.internal.component.model.ComponentAttributeMatcher
import spock.lang.Specification

class VariantAttributeMatchingCacheTest extends Specification {
    def matcher = Mock(ComponentAttributeMatcher)
    def schema = new DefaultAttributesSchema(matcher)
    def immutableAttributesFactory = new DefaultImmutableAttributesFactory()
    def transformRegistrations = Mock(ArtifactTransformRegistrationsInternal)
    def matchingCache = new VariantAttributeMatchingCache(transformRegistrations, schema, new DefaultImmutableAttributesFactory())

    def a1 = Attribute.of("a1", String)
    def a2 = Attribute.of("a2", Integer)
    def c1 = attributes().attribute(a1, "1").attribute(a2, 1).asImmutable()
    def c2 = attributes().attribute(a1, "1").attribute(a2, 2).asImmutable()
    def c3 = attributes().attribute(a1, "1").attribute(a2, 3).asImmutable()

    static class Transform extends ArtifactTransform {
        Transformer<List<File>, File> transformer

        List<File> transform(File input) {
            return transformer.transform(input)
        }
    }

    def "variants are selected using matcher ignoring additional actual attributes and result reused"() {
        def variant1 = Stub(HasAttributes)
        def variant2 = Stub(HasAttributes)
        def variant3 = Stub(HasAttributes)
        def variant4 = Stub(HasAttributes)
        variant1.attributes >> c1
        variant2.attributes >> c2
        variant3.attributes >> c1
        variant4.attributes >> c2

        when:
        def result = matchingCache.selectMatches([variant1, variant2], c1)

        then:
        result == [variant1]
        1 * matcher.ignoreAdditionalProducerAttributes() >> matcher
        1 * matcher.match(schema, schema, [c1, c2], c1) >> [c1]
        0 * matcher._

        when:
        result = matchingCache.selectMatches([variant3, variant4], c1)

        then:
        result == [variant3]
        0 * matcher._
    }

    def "selects first transform that can produce variant that is compatible with requested"() {
        def reg1 = registration(c1, c3, {})
        def reg2 = registration(c1, c2, {})
        def reg3 = registration(c2, c3, {})
        def requested = attributes().attribute(a1, "requested")
        def source = attributes().attribute(a1, "source")

        given:
        transformRegistrations.transforms >> [reg1, reg2, reg3]

        when:
        def result = matchingCache.getGeneratedVariant(source, requested)

        then:
        result.attributes == c2
        result.transformer.is(reg2.transform)

        and:
        1 * matcher.ignoreAdditionalProducerAttributes() >> matcher
        1 * matcher.isMatching(schema, source, c1) >> true
        2 * matcher.ignoreAdditionalConsumerAttributes() >> matcher
        1 * matcher.isMatching(schema, c3, requested) >> false
        1 * matcher.isMatching(schema, c2, requested) >> true
        0 * matcher._
    }

    def "transform match is reused"() {
        def reg1 = registration(c1, c3, {})
        def reg2 = registration(c1, c2, {})
        def requested = attributes().attribute(a1, "requested")
        def source = attributes().attribute(a1, "source")

        given:
        transformRegistrations.transforms >> [reg1, reg2]

        when:
        def result = matchingCache.getGeneratedVariant(source, requested)

        then:
        result.transformer.is(reg2.transform)

        and:
        1 * matcher.ignoreAdditionalProducerAttributes() >> matcher
        1 * matcher.isMatching(schema, source, c1) >> true
        2 * matcher.ignoreAdditionalConsumerAttributes() >> matcher
        1 * matcher.isMatching(schema, c3, requested) >> false
        1 * matcher.isMatching(schema, c2, requested) >> true
        0 * matcher._

        when:
        def result2 = matchingCache.getGeneratedVariant(source, requested)

        then:
        result2.attributes.is(result.attributes)
        result2.transformer.is(result.transformer)

        and:
        0 * matcher._
    }

    def "selects chain of transforms that can produce variant that is compatible with requested"() {
        def c4 = attributes().attribute(a1, "4")
        def c5 = attributes().attribute(a1, "5")
        def requested = attributes().attribute(a1, "requested")
        def source = attributes().attribute(a1, "source")
        def reg1 = registration(c1, c3, { throw new AssertionFailedError() })
        def reg2 = registration(c1, c2, { File f -> [new File(f.name + ".2a"), new File(f.name + ".2b")]})
        def reg3 = registration(c4, c5, { File f -> [new File(f.name + ".5")]})

        given:
        transformRegistrations.transforms >> [reg1, reg2, reg3]

        when:
        def transformer = matchingCache.getGeneratedVariant(source, requested)

        then:
        transformer != null

        and:
        2 * matcher.ignoreAdditionalProducerAttributes() >> matcher
        5 * matcher.ignoreAdditionalConsumerAttributes() >> matcher
        1 * matcher.isMatching(schema, c3, requested) >> false
        1 * matcher.isMatching(schema, c2, requested) >> false
        1 * matcher.isMatching(schema, c5, requested) >> true
        1 * matcher.isMatching(schema, source, c4) >> false
        1 * matcher.isMatching(schema, c2, c4) >> true
        1 * matcher.isMatching(schema, c3, c4) >> false
        1 * matcher.isMatching(schema, source, c1) >> true
        0 * matcher._

        when:
        def result = transformer.transformer.transform(new File("in.txt"))

        then:
        result == [new File("in.txt.2a.5"), new File("in.txt.2b.5")]
    }

    def "prefers direct transformation over indirect"() {
        def c4 = attributes().attribute(a1, "4")
        def c5 = attributes().attribute(a1, "5")
        def requested = attributes().attribute(a1, "requested")
        def source = attributes().attribute(a1, "source")
        def reg1 = registration(c1, c3, { })
        def reg2 = registration(c1, c2, { })
        def reg3 = registration(c4, c5, { })

        given:
        transformRegistrations.transforms >> [reg1, reg2, reg3]

        when:
        def transformer = matchingCache.getGeneratedVariant(source, requested)

        then:
        transformer.transformer.is(reg3.transform)

        and:
        2 * matcher.ignoreAdditionalProducerAttributes() >> matcher
        3 * matcher.ignoreAdditionalConsumerAttributes() >> matcher
        1 * matcher.isMatching(schema, c3, requested) >> false
        1 * matcher.isMatching(schema, c2, requested) >> true
        1 * matcher.isMatching(schema, c5, requested) >> true
        1 * matcher.isMatching(schema, source, c1) >> false
        1 * matcher.isMatching(schema, source, c4) >> true
        0 * matcher._
    }

    def "returns null transformer when none is available to produce requested variant"() {
        def reg1 = new RegisteredArtifactTransform(c1, c3, Transform, {}, new File('output'))
        def reg2 = new RegisteredArtifactTransform(c1, c2, Transform, {}, new File('output'))
        def requested = attributes().attribute(a1, "requested")
        def source = attributes().attribute(a1, "source")

        given:
        transformRegistrations.transforms >> [reg1, reg2]

        when:
        def result = matchingCache.getGeneratedVariant(source, requested)

        then:
        result == null

        and:
        2 * matcher.ignoreAdditionalConsumerAttributes() >> matcher
        1 * matcher.isMatching(schema, c3, requested) >> false
        1 * matcher.isMatching(schema, c2, requested) >> false
        0 * matcher._
    }

    def "caches negative match"() {
        def reg1 = registration(c1, c3, {})
        def reg2 = registration(c1, c2, {})
        def requested = attributes().attribute(a1, "requested")
        def source = attributes().attribute(a1, "source")

        given:
        transformRegistrations.transforms >> [reg1, reg2]

        when:
        def result = matchingCache.getGeneratedVariant(source, requested)

        then:
        result == null

        and:
        2 * matcher.ignoreAdditionalConsumerAttributes() >> matcher
        1 * matcher.isMatching(schema, c3, requested) >> false
        1 * matcher.isMatching(schema, c2, requested) >> false
        0 * matcher._

        when:
        def result2 = matchingCache.getGeneratedVariant(source, requested)

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

    def "empty requested attributes matches any single candidate"() {
        def empty = attributes()

        when:
        def result1 = matchingCache.selectMatches([empty], empty)
        def result2 = matchingCache.selectMatches([c1], empty)

        then:
        result1 == [empty]
        result2 == [c1]
        0 * matcher._
    }

    def "multiple candidates are forwarded when empty requested attributes to matcher to disambiguate"() {
        def empty = attributes()

        when:
        def result1 = matchingCache.selectMatches([c1, c2], empty)

        then:
        result1 == [c1]
        matcher.ignoreAdditionalProducerAttributes() >> matcher
        matcher.match(schema, schema, [c1, c2], empty) >> [c1]
    }

    private DefaultMutableAttributeContainer attributes() {
        new DefaultMutableAttributeContainer(immutableAttributesFactory)
    }

    private RegisteredArtifactTransform registration(AttributeContainer from, AttributeContainer to, Transformer transformer) {
        def reg = Stub(RegisteredArtifactTransform)
        reg.from >> from
        reg.to >> to
        reg.transform >> transformer
        reg
    }
}
