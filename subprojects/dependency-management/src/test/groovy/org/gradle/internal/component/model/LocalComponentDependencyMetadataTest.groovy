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

import com.google.common.base.Optional
import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.api.internal.attributes.DefaultAttributesSchema
import org.gradle.api.internal.attributes.EmptySchema
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.ImmutableAttributesFactory
import org.gradle.api.internal.capabilities.CapabilitiesMetadataInternal
import org.gradle.internal.component.AmbiguousConfigurationSelectionException
import org.gradle.internal.component.IncompatibleConfigurationSelectionException
import org.gradle.internal.component.external.descriptor.DefaultExclude
import org.gradle.util.AttributeTestUtil
import org.gradle.util.SnapshotTestUtil
import org.gradle.util.TestUtil
import spock.lang.Specification
import spock.lang.Unroll

import static com.google.common.collect.ImmutableList.copyOf
import static org.gradle.util.internal.TextUtil.toPlatformLineSeparators

class LocalComponentDependencyMetadataTest extends Specification {
    AttributesSchemaInternal attributesSchema
    ImmutableAttributesFactory factory
    ComponentIdentifier componentId = new ComponentIdentifier() {
        @Override
        String getDisplayName() {
            return "example"
        }
    }

    def setup() {
        attributesSchema = new DefaultAttributesSchema(TestUtil.instantiatorFactory(), SnapshotTestUtil.isolatableFactory())
        factory = AttributeTestUtil.attributesFactory()
    }

    private static VersionConstraint v(String version) {
        new DefaultMutableVersionConstraint(version)
    }

    def "returns this when same target requested"() {
        def selector = Stub(ProjectComponentSelector)
        def dep = new LocalComponentDependencyMetadata(componentId, selector, "from", null, ImmutableAttributes.EMPTY, "to", [] as List, [], false, false, true, false, false, null)

        expect:
        dep.withTarget(selector).is(dep)
    }

    def "selects the target configuration from target component"() {
        def dep = new LocalComponentDependencyMetadata(componentId, Stub(ComponentSelector), "from", null, ImmutableAttributes.EMPTY, "to", [] as List, [], false, false, true, false, false, null)
        def toComponent = Stub(ComponentGraphResolveMetadata)
        def toState = Stub(ComponentGraphResolveState) {
            getMetadata() >> toComponent
        }
        def toConfig = Stub(ConfigurationGraphResolveMetadata) {
            isCanBeConsumed() >> true
            getAttributes() >> attributes([:])
        }

        given:
        toComponent.getConfiguration("to") >> toConfig

        expect:
        dep.selectVariants(attributes([:]), toState, attributesSchema, [] as Set).variants == [toConfig]
    }

    @Unroll("selects configuration '#expected' from target component (#scenario)")
    def "selects the target configuration from target component which matches the attributes"() {
        def dep = new LocalComponentDependencyMetadata(componentId, Stub(ComponentSelector), "from", null, ImmutableAttributes.EMPTY, null, [] as List, [], false, false, true, false, false, null)
        def defaultConfig = defaultConfiguration()
        def toFooConfig = Stub(ConfigurationGraphResolveMetadata) {
            getName() >> 'foo'
            getAttributes() >> attributes(key: 'something')
            isCanBeConsumed() >> true
            getCapabilities() >> Stub(CapabilitiesMetadataInternal)
        }
        def toBarConfig = Stub(ConfigurationGraphResolveMetadata) {
            getName() >> 'bar'
            getAttributes() >> attributes(key: 'something else')
            isCanBeConsumed() >> true
            getCapabilities() >> Stub(CapabilitiesMetadataInternal)
        }
        def toComponent = Stub(ComponentGraphResolveMetadata) {
            getVariantsForGraphTraversal() >> Optional.of(ImmutableList.of(toFooConfig, toBarConfig))
            getAttributesSchema() >> EmptySchema.INSTANCE
        }
        def toState = Stub(ComponentGraphResolveState) {
            getMetadata() >> toComponent
        }
        attributesSchema.attribute(Attribute.of('key', String))
        attributesSchema.attribute(Attribute.of('extra', String))

        given:
        toComponent.getConfiguration("default") >> defaultConfig
        toComponent.getConfiguration("foo") >> toFooConfig
        toComponent.getConfiguration("bar") >> toBarConfig

        expect:
        dep.selectVariants(attributes(queryAttributes), toState, attributesSchema, [] as Set).variants.name as Set == [expected] as Set

        where:
        scenario                                         | queryAttributes                 | expected
        'exact match'                                    | [key: 'something']              | 'foo'
        'exact match'                                    | [key: 'something else']         | 'bar'
        'partial match on key but attribute is optional' | [key: 'something', extra: 'no'] | 'foo'
    }

    def "revalidates default configuration if it has attributes"() {
        def dep = new LocalComponentDependencyMetadata(componentId, Stub(ComponentSelector), "from", null, ImmutableAttributes.EMPTY, Dependency.DEFAULT_CONFIGURATION, [] as List, [], false, false, true, false, false, null)
        def defaultConfig = Stub(ConfigurationGraphResolveMetadata) {
            getName() >> 'default'
            isCanBeConsumed() >> true
            getAttributes() >> attributes(key: 'nothing')
        }
        def toComponent = Stub(ComponentGraphResolveMetadata) {
            getAttributesSchema() >> attributesSchema
            getId() >> Stub(ComponentIdentifier) {
                getDisplayName() >> "[target]"
            }
        }
        def toState = Stub(ComponentGraphResolveState) {
            getMetadata() >> toComponent
        }
        attributesSchema.attribute(Attribute.of('key', String))
        attributesSchema.attribute(Attribute.of('will', String))

        given:
        toComponent.getConfiguration("default") >> defaultConfig

        when:
        dep.selectVariants(attributes(key: 'other'), toState, attributesSchema, [] as Set)*.name as Set

        then:
        def e = thrown(IncompatibleConfigurationSelectionException)
        e.message == toPlatformLineSeparators("""Configuration 'default' in [target] does not match the consumer attributes
Configuration 'default':
  - Incompatible because this component declares attribute 'key' with value 'nothing' and the consumer needed attribute 'key' with value 'other'""")
    }

    def "revalidates explicit configuration selection if it has attributes"() {
        def dep = new LocalComponentDependencyMetadata(componentId, Stub(ComponentSelector), "from", null, ImmutableAttributes.EMPTY, 'bar', [] as List, [], false, false, true, false, false, null)
        def defaultConfig = defaultConfiguration()
        def toFooConfig = Stub(ConfigurationGraphResolveMetadata) {
            getName() >> 'foo'
            getAttributes() >> attributes(key: 'something')
            isCanBeConsumed() >> true
        }
        def toBarConfig = Stub(ConfigurationGraphResolveMetadata) {
            getName() >> 'bar'
            getAttributes() >> attributes(key: 'something else')
            isCanBeConsumed() >> true
        }
        def toComponent = Stub(ComponentGraphResolveMetadata) {
            getId() >> Stub(ComponentIdentifier) {
                getDisplayName() >> "[target]"
            }
            getAttributesSchema() >> EmptySchema.INSTANCE
        }
        def toState = Stub(ComponentGraphResolveState) {
            getMetadata() >> toComponent
        }

        attributesSchema.attribute(Attribute.of('key', String))

        given:
        toComponent.getConfiguration("default") >> defaultConfig
        toComponent.getConfiguration("foo") >> toFooConfig
        toComponent.getConfiguration("bar") >> toBarConfig

        when:
        dep.selectVariants(attributes(key: 'something'), toState, attributesSchema, [] as Set)*.name as Set

        then:
        def e = thrown(IncompatibleConfigurationSelectionException)
        e.message == toPlatformLineSeparators("""Configuration 'bar' in [target] does not match the consumer attributes
Configuration 'bar':
  - Incompatible because this component declares attribute 'key' with value 'something else' and the consumer needed attribute 'key' with value 'something'""")
    }

    @Unroll("selects configuration '#expected' from target component with Java proximity matching strategy (#scenario)")
    def "selects the target configuration from target component with Java proximity matching strategy"() {
        def dep = new LocalComponentDependencyMetadata(componentId, Stub(ComponentSelector), "from", null, ImmutableAttributes.EMPTY, null, [] as List, [], false, false, true, false, false, null)
        def defaultConfig = defaultConfiguration()
        def toFooConfig = Stub(ConfigurationGraphResolveMetadata) {
            getName() >> 'foo'
            getAttributes() >> attributes(fooAttributes)
            isCanBeConsumed() >> true
            getCapabilities() >> Stub(CapabilitiesMetadataInternal)
        }
        def toBarConfig = Stub(ConfigurationGraphResolveMetadata) {
            getName() >> 'bar'
            getAttributes() >> attributes(barAttributes)
            isCanBeConsumed() >> true
            getCapabilities() >> Stub(CapabilitiesMetadataInternal)
        }
        def toComponent = Stub(ComponentGraphResolveMetadata) {
            getVariantsForGraphTraversal() >> Optional.of(ImmutableList.of(toFooConfig, toBarConfig))
            getAttributesSchema() >> attributesSchema
            getId() >> Stub(ComponentIdentifier) {
                getDisplayName() >> "[target]"
            }
        }
        def toState = Stub(ComponentGraphResolveState) {
            getMetadata() >> toComponent
        }
        attributesSchema.attribute(Attribute.of('platform', JavaVersion), {
            it.ordered { a, b -> a <=> b }
            it.ordered(true, { a, b -> a <=> b })
        })
        attributesSchema.attribute(Attribute.of('flavor', String))
        attributesSchema.attribute(Attribute.of('extra', String))

        given:
        toComponent.getConfiguration("default") >> defaultConfig
        toComponent.getConfiguration("foo") >> toFooConfig
        toComponent.getConfiguration("bar") >> toBarConfig

        expect:
        try {
            def result = dep.selectVariants(attributes(queryAttributes), toState, attributesSchema, [] as Set).variants.name as Set
            if (expected == null && result) {
                throw new AssertionError("Expected an ambiguous result, but got $result")
            }
            assert result == [expected] as Set
        } catch (AmbiguousConfigurationSelectionException e) {
            if (expected == null) {
                assert e.message.startsWith(toPlatformLineSeparators("The consumer was configured to find attribute 'platform' with value '${queryAttributes.platform}'${queryAttributes.flavor?", attribute 'flavor' with value '$queryAttributes.flavor'":""}. However we cannot choose between the following variants of [target]:\n  - bar\n  - foo\nAll of them match the consumer attributes:"))
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

    @Unroll("selects configuration '#expected' from target component with Java proximity matching strategy using short-hand notation (#scenario)")
    def "selects the target configuration from target component with Java proximity matching strategy using short-hand notation"() {
        def dep = new LocalComponentDependencyMetadata(componentId, Stub(ComponentSelector), "from", null, ImmutableAttributes.EMPTY, null, [] as List, [], false, false, true, false, false, null)
        def defaultConfig = defaultConfiguration()
        def toFooConfig = Stub(ConfigurationGraphResolveMetadata) {
            getName() >> 'foo'
            getAttributes() >> attributes(fooAttributes)
            isCanBeConsumed() >> true
            getCapabilities() >> Stub(CapabilitiesMetadataInternal)
        }
        def toBarConfig = Stub(ConfigurationGraphResolveMetadata) {
            getName() >> 'bar'
            getAttributes() >> attributes(barAttributes)
            isCanBeConsumed() >> true
            getCapabilities() >> Stub(CapabilitiesMetadataInternal)
        }
        def toComponent = Stub(ComponentGraphResolveMetadata) {
            getVariantsForGraphTraversal() >> Optional.of(ImmutableList.of(toFooConfig, toBarConfig))
            getAttributesSchema() >> attributesSchema
            getId() >> Stub(ComponentIdentifier) {
                getDisplayName() >> "[target]"
            }
        }
        def toState = Stub(ComponentGraphResolveState) {
            getMetadata() >> toComponent
        }
        attributesSchema.attribute(Attribute.of('platform', JavaVersion), {
            it.ordered { a, b -> a <=> b }
            it.ordered(true, { a, b -> a <=> b })
        })
        attributesSchema.attribute(Attribute.of('flavor', String))
        attributesSchema.attribute(Attribute.of('extra', String))

        given:
        toComponent.getConfiguration("default") >> defaultConfig
        toComponent.getConfiguration("foo") >> toFooConfig
        toComponent.getConfiguration("bar") >> toBarConfig

        expect:
        try {
            def result = dep.selectVariants(attributes(queryAttributes), toState, attributesSchema, [] as Set).variants.name as Set
            if (expected == null && result) {
                throw new AssertionError("Expected an ambiguous result, but got $result")
            }
            assert result == [expected] as Set
        } catch (AmbiguousConfigurationSelectionException e) {
            if (expected == null) {
                assert e.message.startsWith(toPlatformLineSeparators("The consumer was configured to find attribute 'platform' with value '${queryAttributes.platform}'${queryAttributes.flavor?", attribute 'flavor' with value '${queryAttributes.flavor}'":""}. However we cannot choose between the following variants of [target]:\n  - bar\n  - foo\nAll of them match the consumer attributes:"))
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

    def "fails to select target configuration when not present in the target component"() {
        def fromId = Stub(ComponentIdentifier) { getDisplayName() >> "thing a" }
        def dep = new LocalComponentDependencyMetadata(fromId, Stub(ComponentSelector), "from", null, ImmutableAttributes.EMPTY, "to", [] as List, [], false, false, true, false, false, null)
        def toComponent = Stub(ComponentGraphResolveMetadata)
        toComponent.id >> Stub(ComponentIdentifier) { getDisplayName() >> "thing b" }
        def toState = Stub(ComponentGraphResolveState) {
            getMetadata() >> toComponent
        }

        given:
        toComponent.getConfiguration("to") >> null

        when:
        dep.selectVariants(attributes([:]), toState, attributesSchema,[] as Set)

        then:
        def e = thrown(ConfigurationNotFoundException)
        e.message == "Thing a declares a dependency from configuration 'from' to configuration 'to' which is not declared in the descriptor for thing b."
    }

    def "excludes nothing when no exclude rules provided"() {
        def moduleExclusions = new ModuleExclusions()
        def dep = new LocalComponentDependencyMetadata(componentId, Stub(ComponentSelector), "from", null, ImmutableAttributes.EMPTY, "to", [] as List, [], false, false, true, false, false, null)

        expect:
        def exclusions = moduleExclusions.excludeAny(copyOf(dep.excludes))
        exclusions == moduleExclusions.nothing()
        exclusions.is(moduleExclusions.excludeAny(copyOf(dep.excludes)))
    }

    def "applies exclude rules when traversing the from configuration"() {
        def exclude1 = new DefaultExclude(DefaultModuleIdentifier.newId("group1", "*"))
        def exclude2 = new DefaultExclude(DefaultModuleIdentifier.newId("group2", "*"))
        def moduleExclusions = new ModuleExclusions()
        def dep = new LocalComponentDependencyMetadata(componentId, Stub(ComponentSelector), "from", null, ImmutableAttributes.EMPTY, "to", [] as List, [exclude1, exclude2], false, false, true, false, false, null)

        expect:
        def exclusions = moduleExclusions.excludeAny(copyOf(dep.excludes))
        exclusions == moduleExclusions.excludeAny(ImmutableList.of(exclude1, exclude2))
        exclusions.is(moduleExclusions.excludeAny(copyOf(dep.excludes)))
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
        def dep = new LocalComponentDependencyMetadata(componentId, Stub(ComponentSelector), "from", null, ImmutableAttributes.EMPTY, null, [] as List, [], false, false, true, false, false, null)
        def defaultConfig = defaultConfiguration()
        def toFooConfig = Stub(ConfigurationGraphResolveMetadata) {
            getName() >> 'foo'
            getAttributes() >> attributes(key: 'something')
            isCanBeConsumed() >> true
            getCapabilities() >> Stub(CapabilitiesMetadataInternal)
        }
        def toBarConfig = Stub(ConfigurationGraphResolveMetadata) {
            getName() >> 'bar'
            getAttributes() >> attributes(key: 'something else')
            isCanBeConsumed() >> true
            getCapabilities() >> Stub(CapabilitiesMetadataInternal)
        }
        def toComponent = Stub(ComponentGraphResolveMetadata) {
            getVariantsForGraphTraversal() >> Optional.of(ImmutableList.of(toFooConfig, toBarConfig))
            getAttributesSchema() >> EmptySchema.INSTANCE
        }
        def toState = Stub(ComponentGraphResolveState) {
            getMetadata() >> toComponent
        }
        def attributeSchemaWithCompatibility = new DefaultAttributesSchema(TestUtil.instantiatorFactory(), SnapshotTestUtil.isolatableFactory())
        attributeSchemaWithCompatibility.attribute(Attribute.of('key', String), {
            it.compatibilityRules.add(EqualsValuesCompatibleRule)
            it.compatibilityRules.add(ValueCompatibleRule)
        })
        attributeSchemaWithCompatibility.attribute(Attribute.of('extra', String))

        given:
        toComponent.getConfiguration("default") >> defaultConfig
        toComponent.getConfiguration("foo") >> toFooConfig
        toComponent.getConfiguration("bar") >> toBarConfig

        expect:
        dep.selectVariants(attributes(queryAttributes), toState, attributeSchemaWithCompatibility, [] as Set).variants.name as Set == [expected] as Set

        where:
        scenario                     | queryAttributes                 | expected
        'exact match'                | [key: 'something else']         | 'bar'
        'compatible value'           | [key: 'other']                  | 'bar'
    }

    def configuration(String name, String... parents) {
        def config = Stub(ConfigurationMetadata)
        config.hierarchy >> ([name] as Set) + (parents as Set)
        return config
    }

    private ImmutableAttributes attributes(Map<String, ?> src) {
        def attributes = factory.mutable()
        src.each { String name, Object value ->
            def key = Attribute.of(name, value.class)
            attributes.attribute(key, value)
        }
        return attributes.asImmutable()
    }

    private ConfigurationGraphResolveMetadata defaultConfiguration() {
        Stub(ConfigurationGraphResolveMetadata) {
            getName() >> 'default'
            isCanBeConsumed() >> true
            getAttributes() >> Mock(AttributeContainerInternal) {
                isEmpty() >> true
            }
        }
    }

    enum JavaVersion {
        JAVA5,
        JAVA6,
        JAVA7,
        JAVA8,
        JAVA9
    }

}
