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

import com.google.common.collect.ImmutableList
import junit.framework.AssertionFailedError
import org.gradle.api.Transformer
import org.gradle.api.artifacts.transform.ArtifactTransform
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.internal.artifacts.VariantTransformRegistry
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.internal.Try
import org.gradle.internal.component.model.AttributeMatcher
import org.gradle.util.AttributeTestUtil
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll

import static org.spockframework.util.CollectionUtil.mapOf

class ConsumerProvidedVariantFinderTest extends Specification {
    def matcher = Mock(AttributeMatcher)
    def schema = Mock(AttributesSchemaInternal)
    def immutableAttributesFactory = AttributeTestUtil.attributesFactory()
    def transformRegistrations = Mock(VariantTransformRegistry)
    def matchingCache = new ConsumerProvidedVariantFinder(transformRegistrations, schema, immutableAttributesFactory)

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

    /**
     * Match all AttributeContainer that contains the same attributes.
     *
     * This method is for writing argument constraint in spock interaction. When search for
     * chains, ConsumerProvidedVariantFinder may create a new instance of the AttributeContainer
     * to call {@link AttributeMatcher#isMatching(AttributeContainerInternal, AttributeContainerInternal)}.
     * So we cannot use the origin object instance to write method specification in spock interaction.
     */
    def attributesIs(AttributeContainer except, Map<Attribute<Object>, Object> vals) {
        return except.keySet().size() == vals.size() && vals.every { entry ->
            except.getAttribute(entry.key) == entry.value
        }
    }

    def "selects transform that can produce variant that is compatible with requested"() {
        def reg1 = registration(c1, c3, {})
        def reg2 = registration(c1, c2, {})
        def reg3 = registration(c2, c3, {})
        def requested = attributes().attribute(a1, "requested")
        def source = attributes().attribute(a1, "source")

        given:
        transformRegistrations.transforms >> [reg1, reg2, reg3]

        when:
        def result = new ConsumerVariantMatchResult()
        matchingCache.collectConsumerVariants(source, requested, result)

        then:
        result.matches.size() == 1
        result.matches.first().attributes == c2
        result.matches.first().transformation.is(reg2.transformationStep)

        and:
        _ * schema.matcher() >> matcher
        _ * matcher.ignoreAdditionalProducerAttributes() >> matcher
        1 * matcher.isMatching(c3, requested) >> false
        1 * matcher.isMatching(c2, requested) >> true
        _ * matcher.ignoreAdditionalConsumerAttributes() >> matcher
        1 * matcher.isMatching(source, c1) >> true
        0 * matcher._
    }

    def "selects all transforms that can produce variant that is compatible with requested"() {
        def reg1 = registration(c1, c3, {})
        def reg2 = registration(c1, c2, {})
        def reg3 = registration(c2, c3, {})
        def requested = attributes().attribute(a1, "requested")
        def source = attributes().attribute(a1, "source")

        given:
        transformRegistrations.transforms >> [reg1, reg2, reg3]

        when:
        def result = new ConsumerVariantMatchResult()
        matchingCache.collectConsumerVariants(source, requested, result)

        then:
        result.matches.size() == 2
        result.matches*.attributes == [c3, c2]
        result.matches*.transformation == [reg1.transformationStep, reg2.transformationStep]

        and:
        _ * schema.matcher() >> matcher
        _ * matcher.ignoreAdditionalProducerAttributes() >> matcher
        1 * matcher.isMatching(c3, requested) >> true
        1 * matcher.isMatching(c2, requested) >> true
        _ * matcher.ignoreAdditionalConsumerAttributes() >> matcher
        1 * matcher.isMatching(source, c1) >> true
        1 * matcher.isMatching(source, c2) >> false
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
        def result = new ConsumerVariantMatchResult()
        matchingCache.collectConsumerVariants(source, requested, result)

        then:
        def match = result.matches.first()
        match.transformation.is(reg2.transformationStep)

        and:
        _ * schema.matcher() >> matcher
        _ * matcher.ignoreAdditionalProducerAttributes() >> matcher
        1 * matcher.isMatching(source, c1) >> true
        _ * matcher.ignoreAdditionalConsumerAttributes() >> matcher
        1 * matcher.isMatching(c3, requested) >> false
        1 * matcher.isMatching(c2, requested) >> true
        0 * matcher._

        when:
        def result2 = new ConsumerVariantMatchResult()
        matchingCache.collectConsumerVariants(source, requested, result2)

        then:
        def match2 = result2.matches.first()
        match2.attributes.is(match.attributes)
        match2.transformation.is(match.transformation)

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
        def matchResult = new ConsumerVariantMatchResult()
        matchingCache.collectConsumerVariants(source, requested, matchResult)

        then:
        def transformer = matchResult.matches.first()
        transformer != null

        and:
        _ * schema.matcher() >> matcher
        _ * matcher.ignoreAdditionalProducerAttributes() >> matcher
        _ * matcher.ignoreAdditionalConsumerAttributes() >> matcher
        1 * matcher.isMatching(c3, requested) >> false
        1 * matcher.isMatching(c2, requested) >> false
        1 * matcher.isMatching(c5, requested) >> true
        1 * matcher.isMatching(source, c4) >> false
        1 * matcher.isMatching(c3, { attributesIs(it, mapOf(a1, "4")) }) >> false
        1 * matcher.isMatching(c2, { attributesIs(it, mapOf(a1, "4")) }) >> true
        1 * matcher.isMatching(source, c1) >> true
        1 * matcher.isMatching(c5, { attributesIs(it, mapOf(a1, "4")) }) >> false
        0 * matcher._

        when:
        def result = transformer.transformation.transform(initialSubject("in.txt"), Mock(ExecutionGraphDependenciesResolver)).get()

        then:
        result.files == [new File("in.txt.2a.5"), new File("in.txt.2b.5")]
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
        def result = new ConsumerVariantMatchResult()
        matchingCache.collectConsumerVariants(source, requested, result)

        then:
        result.matches.first().transformation.is(reg3.transformationStep)

        and:
        _ * schema.matcher() >> matcher
        _ * matcher.ignoreAdditionalProducerAttributes() >> matcher
        _ * matcher.ignoreAdditionalConsumerAttributes() >> matcher
        1 * matcher.isMatching(c3, requested) >> false
        1 * matcher.isMatching(c2, requested) >> true
        1 * matcher.isMatching(c5, requested) >> true
        1 * matcher.isMatching(source, c1) >> false
        1 * matcher.isMatching(source, c4) >> true
        0 * matcher._
    }

    @Unroll
    def "prefers shortest chain of transforms #registrationsIndex"() {
        def transform1 = Mock(Transformer)
        def transform2 = Mock(Transformer)
        def c4 = attributes().attribute(a1, "4")
        def c5 = attributes().attribute(a1, "5")
        def requested = attributes().attribute(a1, "requested")
        def source = attributes().attribute(a1, "source")
        def reg1 = registration(c2, c3, {})
        def reg2 = registration(c2, c4, transform1)
        def reg3 = registration(c3, c4, {})
        def reg4 = registration(c4, c5, transform2)
        def registrations = [reg1, reg2, reg3, reg4]

        given:
        transformRegistrations.transforms >> [registrations[registrationsIndex[0]], registrations[registrationsIndex[1]], registrations[registrationsIndex[2]], registrations[registrationsIndex[3]]]

        when:
        def result = new ConsumerVariantMatchResult()
        matchingCache.collectConsumerVariants(source, requested, result)

        then:
        result.matches.size() == 1

        and:
        _ * schema.matcher() >> matcher
        _ * matcher.ignoreAdditionalProducerAttributes() >> matcher
        _ * matcher.ignoreAdditionalConsumerAttributes() >> matcher
        1 * matcher.isMatching(c3, requested) >> false
        1 * matcher.isMatching(c4, requested) >> false
        1 * matcher.isMatching(c5, requested) >> true
        1 * matcher.isMatching(source, c4) >> false
        1 * matcher.isMatching(c4, { attributesIs(it, mapOf(a1, "4")) }) >> true
        1 * matcher.isMatching(c3, { attributesIs(it, mapOf(a1, "4")) }) >> false
        1 * matcher.isMatching(c5, { attributesIs(it, mapOf(a1, "4")) }) >> false
        1 * matcher.isMatching(source, c2) >> true
        1 * matcher.isMatching(source, c3) >> false
        0 * matcher._

        when:
        def files = result.matches.first().transformation.transform(initialSubject("a"), Mock(ExecutionGraphDependenciesResolver)).get().files

        then:
        files == [new File("d"), new File("e")]
        transform1.transform(new File("a")) >> [new File("b"), new File("c")]
        transform2.transform(new File("b")) >> [new File("d")]
        transform2.transform(new File("c")) >> [new File("e")]

        where:
        registrationsIndex << (0..3).permutations()
    }

    @Issue("gradle/gradle#7061")
    def "selects chain of transforms that only all the attributes are satisfied"() {
        def c1 = attributes().attribute(a1, "1").attribute(a2, 1).asImmutable()
        def c4 = attributes().attribute(a1, "2").attribute(a2, 2).asImmutable()
        def c5 = attributes().attribute(a1, "2").attribute(a2, 3).asImmutable()
        def c6 = attributes().attribute(a1, "2").asImmutable()
        def c7 = attributes().attribute(a1, "3").asImmutable()
        def requested = attributes().attribute(a1, "3").attribute(a2, 3).asImmutable()
        def source = c1
        def reg1 = registration(c1, c4, {})
        def reg2 = registration(c1, c5, {})
        def reg3 = registration(c6, c7, {})

        given:
        transformRegistrations.transforms >> [reg1, reg2, reg3]

        when:
        def result = new ConsumerVariantMatchResult()
        matchingCache.collectConsumerVariants(source, requested, result)

        then:
        result.matches.size() == 1

        and:
        _ * schema.matcher() >> matcher
        _ * matcher.ignoreAdditionalProducerAttributes() >> matcher
        _ * matcher.ignoreAdditionalConsumerAttributes() >> matcher
        1 * matcher.isMatching(c4, requested) >> false
        1 * matcher.isMatching(c5, requested) >> false
        1 * matcher.isMatching(c7, requested) >> true
        1 * matcher.isMatching(source, c6) >> false
        1 * matcher.isMatching(c4, { attributesIs(it, mapOf(a1, "2", a2, 3)) }) >> false // "2" 3 ; c5
        1 * matcher.isMatching(c5, { attributesIs(it, mapOf(a1, "2", a2, 3)) }) >> true
        1 * matcher.isMatching(source, c1) >> true
        1 * matcher.isMatching(c7, { attributesIs(it, mapOf(a1, "2", a2, 3)) }) >> false
        0 * matcher._

        expect:
        result.matches.size() == 1
    }

    def "returns empty list when no transforms are available to produce requested variant"() {
        def reg1 = registration(c1, c3, { })
        def reg2 = registration(c1, c2, { })
        def requested = attributes().attribute(a1, "requested")
        def source = attributes().attribute(a1, "source")

        given:
        transformRegistrations.transforms >> [reg1, reg2]

        when:
        def result = new ConsumerVariantMatchResult()
        matchingCache.collectConsumerVariants(source, requested, result)

        then:
        result.matches.empty

        and:
        _ * schema.matcher() >> matcher
        _ * matcher.ignoreAdditionalConsumerAttributes() >> matcher
        1 * matcher.isMatching(c3, requested) >> false
        1 * matcher.isMatching(c2, requested) >> false
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
        def result = new ConsumerVariantMatchResult()
        matchingCache.collectConsumerVariants(source, requested, result)

        then:
        result.matches.empty

        and:
        _ * schema.matcher() >> matcher
        _ * matcher.ignoreAdditionalConsumerAttributes() >> matcher
        1 * matcher.isMatching(c3, requested) >> false
        1 * matcher.isMatching(c2, requested) >> false
        0 * matcher._

        when:
        def result2 = new ConsumerVariantMatchResult()
        matchingCache.collectConsumerVariants(source, requested, result2)

        then:
        result2.matches.empty

        and:
        0 * matcher._
    }

    private static TransformationSubject initialSubject(String path) {
        TransformationSubject.initial(new File(path))
    }

    private AttributeContainerInternal attributes() {
        immutableAttributesFactory.mutable()
    }

    private VariantTransformRegistry.Registration registration(AttributeContainer from, AttributeContainer to, Transformer<List<File>, File> transformer) {
        def reg = Stub(VariantTransformRegistry.Registration)
        reg.from >> from
        reg.to >> to
        reg.transformationStep >> Stub(TransformationStep) {
            transform(_ as TransformationSubject, _ as ExecutionGraphDependenciesResolver) >> { TransformationSubject subject, ExecutionGraphDependenciesResolver dependenciesResolver ->
                return Try.successful(subject.createSubjectFromResult(ImmutableList.copyOf(subject.files.collectMany { transformer.transform(it) })))
            }
        }
        reg
    }
}
