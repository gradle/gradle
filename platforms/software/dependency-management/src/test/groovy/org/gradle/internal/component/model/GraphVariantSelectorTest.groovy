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

package org.gradle.internal.component.model

import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.internal.artifacts.DependencyManagementTestUtil
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.local.model.LocalComponentGraphResolveMetadata
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState
import org.gradle.internal.component.resolution.failure.exception.VariantSelectionByAttributesException
import org.gradle.internal.component.resolution.failure.exception.VariantSelectionByNameException
import org.gradle.util.AttributeTestUtil
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.util.internal.TextUtil.toPlatformLineSeparators

/**
 * Tests {@link GraphVariantSelector}.
 */
class GraphVariantSelectorTest extends Specification {

    GraphVariantSelector variantSelector = new GraphVariantSelector(
        AttributeTestUtil.services(),
        DependencyManagementTestUtil.newFailureHandler()
    )

    ComponentIdentifier toComponentId = Stub(ComponentIdentifier) {
        getDisplayName() >> "[target]"
    }

    ComponentGraphResolveMetadata toComponentMetadata = new LocalComponentGraphResolveMetadata(
        Stub(ModuleVersionIdentifier),
        toComponentId,
        "status",
        ImmutableAttributesSchema.EMPTY
    )

    LocalComponentGraphResolveState toComponent = Mock(LocalComponentGraphResolveState) {
        getMetadata() >> toComponentMetadata
        getId() >> toComponentId
        getCandidatesForGraphVariantSelection() >> Mock(LocalComponentGraphResolveState.LocalComponentGraphSelectionCandidates)
    }

    def "selects the target variant by configuration name from target component"() {
        def toConfig = variant("to", ImmutableAttributes.EMPTY)
        toComponent.getCandidatesForGraphVariantSelection().getVariantByConfigurationName("to") >> toConfig

        expect:
        variantSelector.selectVariantByConfigurationName("to", AttributeTestUtil.attributes([:]), toComponent, ImmutableAttributesSchema.EMPTY) == toConfig
    }

    @Unroll("selects variant '#expected' from target component (#scenario)")
    def "selects the variant from target component that matches the attributes"() {
        def toFooVariant = variant('foo', attributes(key: 'something'))
        def toBarVariant = variant('bar', attributes(key: 'something else'))
        toComponent.getCandidatesForGraphVariantSelection().getVariantsForAttributeMatching() >> [toFooVariant, toBarVariant]

        def schema = AttributeTestUtil.immutableSchema {
            attribute(Attribute.of('key', String))
            attribute(Attribute.of('extra', String))
        }

        expect:
        selectVariant(attributes(queryAttributes), schema).name == expected

        where:
        scenario                                         | queryAttributes                 | expected
        'exact match'                                    | [key: 'something']              | 'foo'
        'exact match'                                    | [key: 'something else']         | 'bar'
        'partial match on key but attribute is optional' | [key: 'something', extra: 'no'] | 'foo'
    }

    def "revalidates legacy variant if it has attributes"() {
        def conf = variant('default', AttributeTestUtil.attributes(key: 'nothing'))
        toComponent.getCandidatesForGraphVariantSelection().getLegacyVariant() >> conf

        def schema = AttributeTestUtil.immutableSchema {
            attribute(Attribute.of('key', String))
            attribute(Attribute.of('will', String))
        }

        when:
        variantSelector.selectLegacyVariant(
            AttributeTestUtil.attributes(key: 'other'),
            toComponent,
            schema,
            variantSelector.getFailureHandler()
        )

        then:
        def e = thrown(VariantSelectionByNameException)
        e.message == toPlatformLineSeparators("""Configuration 'default' in [target] does not match the consumer attributes
Configuration 'default':
  - Incompatible because this component declares attribute 'key' with value 'nothing' and the consumer needed attribute 'key' with value 'other'""")
    }

    def "revalidates variant selected by configuration name if it has attributes"() {
        def conf = variant('bar', AttributeTestUtil.attributes(key: 'something else'))
        toComponent.getCandidatesForGraphVariantSelection().getVariantByConfigurationName("bar") >> conf

        def schema = AttributeTestUtil.immutableSchema {
            attribute(Attribute.of('key', String))
        }

        when:
        variantSelector.selectVariantByConfigurationName(
            'bar',
            AttributeTestUtil.attributes(key: 'something'),
            toComponent,
            schema
        )

        then:
        def e = thrown(VariantSelectionByNameException)
        e.message == toPlatformLineSeparators("""Configuration 'bar' in [target] does not match the consumer attributes
Configuration 'bar':
  - Incompatible because this component declares attribute 'key' with value 'something else' and the consumer needed attribute 'key' with value 'something'""")
    }

    @Unroll("selects variant '#expected' from target component with Java proximity matching strategy (#scenario)")
    def "selects the variant from target component with Java proximity matching strategy"() {
        def toFooVariant = variant('foo', attributes(fooAttributes))
        def toBarVariant = variant('bar', attributes(barAttributes))
        toComponent.getCandidatesForGraphVariantSelection().getVariantsForAttributeMatching() >> [toFooVariant, toBarVariant]

        def schema = AttributeTestUtil.immutableSchema {
            attribute(Attribute.of('platform', JavaVersion), {
                it.ordered { a, b -> a <=> b }
                it.ordered(true, { a, b -> a <=> b })
            })
            attribute(Attribute.of('flavor', String))
            attribute(Attribute.of('extra', String))
        }

        expect:
        try {
            def result = selectVariant(attributes(queryAttributes), schema)
            if (expected == null && result) {
                throw new Exception("Expected an ambiguous result, but got $result")
            }
            assert result.name == expected
        } catch (VariantSelectionByAttributesException e) {
            if (expected == null) {
                def distinguisher = queryAttributes.containsKey('flavor') ? 'extra' : 'flavor'
                def distinguishingValues = distinguisher == 'flavor' ? "\n  - Value: 'paid' selects variant: 'bar'\n  - Value: 'free' selects variant: 'foo'" : "\n  - Value: 'bar' selects variant: 'bar'\n  - Value: 'foo' selects variant: 'foo'"
                def expectedMsg = "The consumer was configured to find ${queryAttributes.flavor?"attribute 'flavor' with value '$queryAttributes.flavor', ":""}attribute 'platform' with value '${queryAttributes.platform}'. There are several available matching variants of [target]\nThe only attribute distinguishing these variants is '$distinguisher'. Add this attribute to the consumer's configuration to resolve the ambiguity:${distinguishingValues}"
                assert e.message.startsWith(toPlatformLineSeparators(expectedMsg))
            } else {
                throw e
            }
        }

        where:
        scenario                                                    | queryAttributes                               | fooAttributes                                 | barAttributes                                 | expected
        'exact match is found'                                      | [platform: JavaVersion.JAVA7]                 | [platform: JavaVersion.JAVA7]                 | [:]                                           | 'foo'
        'exact match is found'                                      | [platform: JavaVersion.JAVA8]                 | [platform: JavaVersion.JAVA7]                 | [platform: JavaVersion.JAVA8]                 | 'bar'
        'Java 7  is compatible with Java 8'                         | [platform: JavaVersion.JAVA8]                 | [platform: JavaVersion.JAVA7]                 | [:]                                           | 'foo'
        'Java 7 is closer to Java 8'                                | [platform: JavaVersion.JAVA8]                 | [platform: JavaVersion.JAVA6]                 | [platform: JavaVersion.JAVA7]                 | 'bar'
        'Java 8 is not compatible but Java 6 is'                    | [platform: JavaVersion.JAVA7]                 | [platform: JavaVersion.JAVA8]                 | [platform: JavaVersion.JAVA6]                 | 'bar'
        'compatible platforms, but additional attributes unmatched' | [platform: JavaVersion.JAVA8]                 | [platform: JavaVersion.JAVA6, flavor: 'free'] | [platform: JavaVersion.JAVA6, flavor: 'paid'] | null
        'compatible platforms, but one closer'                      | [platform: JavaVersion.JAVA8]                 | [platform: JavaVersion.JAVA6, flavor: 'free'] | [platform: JavaVersion.JAVA7, flavor: 'paid'] | 'bar'
        'exact match and multiple attributes'                       | [platform: JavaVersion.JAVA8, flavor: 'free'] | [platform: JavaVersion.JAVA8, flavor: 'free'] | [platform: JavaVersion.JAVA7, flavor: 'free'] | 'foo'
        'partial match and multiple attributes'                     | [platform: JavaVersion.JAVA8, flavor: 'free'] | [platform: JavaVersion.JAVA8]                 | [platform: JavaVersion.JAVA7]                 | 'foo'
        'close match and multiple attributes'                       | [platform: JavaVersion.JAVA8, flavor: 'free'] | [platform: JavaVersion.JAVA6, flavor: 'free'] | [platform: JavaVersion.JAVA7, flavor: 'free'] | 'bar'
        'close partial match and multiple attributes'               | [platform: JavaVersion.JAVA8, flavor: 'free'] | [platform: JavaVersion.JAVA6]                 | [platform: JavaVersion.JAVA7]                 | 'bar'
        'exact match and partial match'                             | [platform: JavaVersion.JAVA8, flavor: 'free'] | [platform: JavaVersion.JAVA6]                 | [platform: JavaVersion.JAVA7, flavor: 'free'] | 'bar'
        'no exact match but partial match'                          | [platform: JavaVersion.JAVA8, flavor: 'free'] | [platform: JavaVersion.JAVA6]                 | [platform: JavaVersion.JAVA7, flavor: 'paid'] | 'foo'
        'no exact match but ambiguous partial match'                | [platform: JavaVersion.JAVA8, flavor: 'free'] | [platform: JavaVersion.JAVA6, extra: 'foo']   | [platform: JavaVersion.JAVA6, extra: 'bar']   | null
        'no exact match but best partial match'                     | [platform: JavaVersion.JAVA8, flavor: 'free'] | [platform: JavaVersion.JAVA8, extra: 'foo']   | [platform: JavaVersion.JAVA6, extra: 'bar']   | 'foo'

    }

    @Unroll("selects variant '#expected' from target component with Java proximity matching strategy using short-hand notation (#scenario)")
    def "selects variant from target component with Java proximity matching strategy using short-hand notation"() {
        def toFooVariant = variant('foo', attributes(fooAttributes))
        def toBarVariant = variant('bar', attributes(barAttributes))
        toComponent.getCandidatesForGraphVariantSelection().getVariantsForAttributeMatching() >> [toFooVariant, toBarVariant]

        def schema = AttributeTestUtil.immutableSchema {
            attribute(Attribute.of('platform', JavaVersion), {
                it.ordered { a, b -> a <=> b }
                it.ordered(true, { a, b -> a <=> b })
            })
            attribute(Attribute.of('flavor', String))
            attribute(Attribute.of('extra', String))
        }

        expect:
        try {
            def result = selectVariant(attributes(queryAttributes), schema)
            if (expected == null && result) {
                throw new Exception("Expected an ambiguous result, but got $result")
            }
            assert result.name == expected
        } catch (VariantSelectionByAttributesException e) {
            if (expected == null) {
                def distinguisher = queryAttributes.containsKey('flavor') ? 'extra' : 'flavor'
                def distinguishingValues = distinguisher == 'flavor' ? "\n  - Value: 'paid' selects variant: 'bar'\n  - Value: 'free' selects variant: 'foo'" : "\n  - Value: 'bar' selects variant: 'bar'\n  - Value: 'foo' selects variant: 'foo'"
                def expectedMsg = "The consumer was configured to find ${queryAttributes.flavor?"attribute 'flavor' with value '${queryAttributes.flavor}', ":""}attribute 'platform' with value '${queryAttributes.platform}'. There are several available matching variants of [target]\nThe only attribute distinguishing these variants is '$distinguisher'. Add this attribute to the consumer's configuration to resolve the ambiguity:${distinguishingValues}"
                assert e.message.startsWith(toPlatformLineSeparators(expectedMsg))
            } else {
                throw e
            }
        }

        where:
        scenario                                                    | queryAttributes                               | fooAttributes                                 | barAttributes                                 | expected
        'exact match is found'                                      | [platform: JavaVersion.JAVA7]                 | [platform: JavaVersion.JAVA7]                 | [:]                                           | 'foo'
        'exact match is found'                                      | [platform: JavaVersion.JAVA8]                 | [platform: JavaVersion.JAVA7]                 | [platform: JavaVersion.JAVA8]                 | 'bar'
        'Java 7  is compatible with Java 8'                         | [platform: JavaVersion.JAVA8]                 | [platform: JavaVersion.JAVA7]                 | [:]                                           | 'foo'
        'Java 7 is closer to Java 8'                                | [platform: JavaVersion.JAVA8]                 | [platform: JavaVersion.JAVA6]                 | [platform: JavaVersion.JAVA7]                 | 'bar'
        'Java 8 is not compatible but Java 6 is'                    | [platform: JavaVersion.JAVA7]                 | [platform: JavaVersion.JAVA8]                 | [platform: JavaVersion.JAVA6]                 | 'bar'
        'compatible platforms, but additional attributes unmatched' | [platform: JavaVersion.JAVA8]                 | [platform: JavaVersion.JAVA6, flavor: 'free'] | [platform: JavaVersion.JAVA6, flavor: 'paid'] | null
        'compatible platforms, but one closer'                      | [platform: JavaVersion.JAVA8]                 | [platform: JavaVersion.JAVA6, flavor: 'free'] | [platform: JavaVersion.JAVA7, flavor: 'paid'] | 'bar'
        'exact match and multiple attributes'                       | [platform: JavaVersion.JAVA8, flavor: 'free'] | [platform: JavaVersion.JAVA8, flavor: 'free'] | [platform: JavaVersion.JAVA7, flavor: 'free'] | 'foo'
        'partial match and multiple attributes'                     | [platform: JavaVersion.JAVA8, flavor: 'free'] | [platform: JavaVersion.JAVA8]                 | [platform: JavaVersion.JAVA7]                 | 'foo'
        'close match and multiple attributes'                       | [platform: JavaVersion.JAVA8, flavor: 'free'] | [platform: JavaVersion.JAVA6, flavor: 'free'] | [platform: JavaVersion.JAVA7, flavor: 'free'] | 'bar'
        'close partial match and multiple attributes'               | [platform: JavaVersion.JAVA8, flavor: 'free'] | [platform: JavaVersion.JAVA6]                 | [platform: JavaVersion.JAVA7]                 | 'bar'
        'exact match and partial match'                             | [platform: JavaVersion.JAVA8, flavor: 'free'] | [platform: JavaVersion.JAVA6]                 | [platform: JavaVersion.JAVA7, flavor: 'free'] | 'bar'
        'no exact match but partial match'                          | [platform: JavaVersion.JAVA8, flavor: 'free'] | [platform: JavaVersion.JAVA6]                 | [platform: JavaVersion.JAVA7, flavor: 'paid'] | 'foo'
        'no exact match but ambiguous partial match'                | [platform: JavaVersion.JAVA8, flavor: 'free'] | [platform: JavaVersion.JAVA6, extra: 'foo']   | [platform: JavaVersion.JAVA6, extra: 'bar']   | null
        'no exact match but best partial match'                     | [platform: JavaVersion.JAVA8, flavor: 'free'] | [platform: JavaVersion.JAVA8, extra: 'foo']   | [platform: JavaVersion.JAVA6, extra: 'bar']   | 'foo'
    }

    def "fails to select variant by configuration name when not present in the target component"() {
        when:
        variantSelector.selectVariantByConfigurationName(
            "to",
            AttributeTestUtil.attributes([:]),
            toComponent,
            ImmutableAttributesSchema.EMPTY
        )

        then:
        def e = thrown(VariantSelectionByNameException)
        e.message == "A dependency was declared on configuration 'to' of '[target]' but no variant with that configuration name exists."
    }

    static class EqualsValuesCompatibleRule implements AttributeCompatibilityRule<String> {
        @Override
        void execute(CompatibilityCheckDetails<String> details) {
            def requested = details.consumerValue
            def candidate = details.producerValue
            if (requested == candidate) { // simulate exact match
                details.compatible()
            }
        }
    }

    static class ValueCompatibleRule implements AttributeCompatibilityRule<String> {
        @Override
        void execute(CompatibilityCheckDetails<String> details) {
            def requested = details.consumerValue
            def candidate = details.producerValue
            if (requested == 'other' && candidate == 'something else') { // simulate compatible match
                details.compatible()
            }
        }
    }

    @Unroll("can select a compatible attribute value (#scenario)")
    def "can select a compatible attribute value"() {
        def toFooVariant = variant('foo', attributes(key: 'something'))
        def toBarVariant = variant('bar', attributes(key: 'something else'))
        toComponent.getCandidatesForGraphVariantSelection().getVariantsForAttributeMatching() >> [toFooVariant, toBarVariant]

        def attributeSchemaWithCompatibility = AttributeTestUtil.immutableSchema {
            attribute(Attribute.of('key', String), {
                it.compatibilityRules.add(EqualsValuesCompatibleRule)
                it.compatibilityRules.add(ValueCompatibleRule)
            })
            attribute(Attribute.of('extra', String))
        }

        expect:
        selectVariant(attributes(queryAttributes), attributeSchemaWithCompatibility).name == expected

        where:
        scenario                     | queryAttributes                 | expected
        'exact match'                | [key: 'something else']         | 'bar'
        'compatible value'           | [key: 'other']                  | 'bar'
    }

    private VariantGraphResolveState selectVariant(
        ImmutableAttributes attributes,
        ImmutableAttributesSchema schema
    ) {
        variantSelector.selectByAttributeMatching(
            attributes,
            [] as Set,
            toComponent,
            schema,
            []
        )
    }

    private ImmutableAttributes attributes(Map<String, ?> src) {
        def attributes = AttributeTestUtil.attributesFactory().mutable()
        src.each { String name, Object value ->
            def key = Attribute.of(name, value.class)
            attributes.attribute(key as Attribute<Object>, value)
        }
        return attributes.asImmutable()
    }

    private VariantGraphResolveState variant(String name, AttributeContainerInternal attributes) {
        def variant = Stub(VariantGraphResolveState) {
            getName() >> name
            getAttributes() >> attributes
            getCapabilities() >> ImmutableCapabilities.EMPTY
            getMetadata() >> Stub(VariantGraphResolveMetadata) {
                getName() >> name
                getAttributes() >> attributes
            }
        }
        return variant
    }

    enum JavaVersion {
        JAVA5,
        JAVA6,
        JAVA7,
        JAVA8,
        JAVA9
    }

}
