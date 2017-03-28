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

import org.gradle.api.artifacts.ModuleVersionSelector
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.internal.artifacts.DefaultImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.api.internal.attributes.DefaultAttributesSchema
import org.gradle.api.internal.attributes.DefaultImmutableAttributesFactory
import org.gradle.api.internal.attributes.DefaultMutableAttributeContainer
import org.gradle.api.internal.attributes.EmptySchema
import org.gradle.api.internal.attributes.ImmutableAttributesFactory
import org.gradle.internal.component.AmbiguousConfigurationSelectionException
import org.gradle.internal.component.IncompatibleConfigurationSelectionException
import org.gradle.internal.component.external.descriptor.DefaultExclude
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.local.model.LocalConfigurationMetadata
import org.gradle.util.TestUtil
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.util.TextUtil.toPlatformLineSeparators

class LocalComponentDependencyMetadataTest extends Specification {
    AttributesSchemaInternal attributesSchema
    ImmutableAttributesFactory factory

    def setup() {
        attributesSchema = new DefaultAttributesSchema(new ComponentAttributeMatcher(), TestUtil.instantiatorFactory())
        factory = new DefaultImmutableAttributesFactory()
    }

    def "returns this when same version requested"() {
        def dep = new LocalComponentDependencyMetadata(DefaultModuleComponentSelector.newSelector("a", "b", "12"), DefaultModuleVersionSelector.newSelector("a", "b", "12"), "from", null, "to", [] as Set, [], false, false, true)

        expect:
        dep.withRequestedVersion("12").is(dep)
        dep.withTarget(DefaultModuleComponentSelector.newSelector("a", "b", "12")).is(dep)
    }

    def "returns this when same target requested"() {
        def selector = Stub(ProjectComponentSelector)
        def dep = new LocalComponentDependencyMetadata(selector, Stub(ModuleVersionSelector), "from", null, "to", [] as Set, [], false, false, true)

        expect:
        dep.withTarget(selector).is(dep)
    }

    def "selects the target configuration from target component"() {
        def dep = new LocalComponentDependencyMetadata(Stub(ComponentSelector), Stub(ModuleVersionSelector), "from", null, "to", [] as Set, [], false, false, true)
        def fromComponent = Stub(ComponentResolveMetadata)
        def toComponent = Stub(ComponentResolveMetadata)
        def fromConfig = Stub(LocalConfigurationMetadata) {
            isCanBeResolved() >> true
            getAttributes() >> attributes([:])
        }
        def toConfig = Stub(LocalConfigurationMetadata) {
            isCanBeConsumed() >> true
            getAttributes() >> attributes([:])
        }
        fromConfig.hierarchy >> ["from"]

        given:
        toComponent.getConfiguration("to") >> toConfig

        expect:
        dep.selectConfigurations(fromComponent, fromConfig, toComponent, attributesSchema) == [toConfig] as Set
    }

    @Unroll("selects configuration '#expected' from target component (#scenario)")
    def "selects the target configuration from target component which matches the attributes"() {
        def dep = new LocalComponentDependencyMetadata(Stub(ComponentSelector), Stub(ModuleVersionSelector), "from", null, null, [] as Set, [], false, false, true)
        def fromComponent = Stub(ComponentResolveMetadata)
        def fromConfig = Stub(LocalConfigurationMetadata) {
            getAttributes() >> attributes(queryAttributes)
        }
        fromConfig.hierarchy >> ["from"]
        def defaultConfig = defaultConfiguration()
        def toFooConfig = Stub(LocalConfigurationMetadata) {
            getName() >> 'foo'
            getAttributes() >> attributes(key: 'something')
            isCanBeConsumed() >> true
        }
        def toBarConfig = Stub(LocalConfigurationMetadata) {
            getName() >> 'bar'
            getAttributes() >> attributes(key: 'something else')
            isCanBeConsumed() >> true
        }
        def toComponent = Stub(ComponentResolveMetadata) {
            getConsumableConfigurationsHavingAttributes() >> [toFooConfig, toBarConfig]
            getAttributesSchema() >> EmptySchema.INSTANCE
        }
        attributesSchema.attribute(Attribute.of('key', String), {
            if (allowMissing) {
                it.compatibilityRules.assumeCompatibleWhenMissing()
            }
        })
        attributesSchema.attribute(Attribute.of('extra', String), {
            if (allowMissing) {
                it.compatibilityRules.assumeCompatibleWhenMissing()
            }
        })

        given:
        toComponent.getConfiguration("default") >> defaultConfig
        toComponent.getConfiguration("foo") >> toFooConfig
        toComponent.getConfiguration("bar") >> toBarConfig

        expect:
        dep.selectConfigurations(fromComponent, fromConfig, toComponent, attributesSchema)*.name as Set == [expected] as Set

        where:
        scenario                                         | queryAttributes                 | allowMissing | expected
        'exact match'                                    | [key: 'something']              | false        | 'foo'
        'exact match'                                    | [key: 'something else']         | false        | 'bar'
        'no match'                                       | [key: 'other']                  | false        | 'default'
        'partial match on key but attribute is required' | [key: 'something', extra: 'no'] | false        | 'default'
        'partial match on key but attribute is optional' | [key: 'something', extra: 'no'] | true         | 'foo'
    }

    def "revalidates default configuration if it has attributes"() {
        def dep = new LocalComponentDependencyMetadata(Stub(ComponentSelector), Stub(ModuleVersionSelector), "from", null, null, [] as Set, [], false, false, true)
        def fromComponent = Stub(ComponentResolveMetadata)
        def fromConfig = Stub(LocalConfigurationMetadata) {
            getAttributes() >> attributes(key: 'other')
        }
        fromConfig.hierarchy >> ["from"]
        def defaultConfig = Stub(LocalConfigurationMetadata) {
            getName() >> 'default'
            isCanBeResolved() >> true
            isCanBeConsumed() >> true
            getAttributes() >> attributes(will: 'fail')
        }
        def toFooConfig = Stub(LocalConfigurationMetadata) {
            getName() >> 'foo'
            getAttributes() >> attributes(key: 'something')
            isCanBeConsumed() >> true
        }
        def toBarConfig = Stub(LocalConfigurationMetadata) {
            getName() >> 'bar'
            getAttributes() >> attributes(key: 'something else')
            isCanBeConsumed() >> true
        }
        def toComponent = Stub(ComponentResolveMetadata) {
            getConsumableConfigurationsHavingAttributes() >> [toFooConfig, toBarConfig]
            getAttributesSchema() >> attributesSchema
            getComponentId() >> Stub(ComponentIdentifier) {
                getDisplayName() >> "<target>"
            }
        }
        attributesSchema.attribute(Attribute.of('key', String))
        attributesSchema.attribute(Attribute.of('will', String))

        given:
        toComponent.getConfiguration("default") >> defaultConfig
        toComponent.getConfiguration("foo") >> toFooConfig
        toComponent.getConfiguration("bar") >> toBarConfig

        when:
        dep.selectConfigurations(fromComponent, fromConfig, toComponent, attributesSchema)*.name as Set

        then:
        def e = thrown(IncompatibleConfigurationSelectionException)
        e.message == toPlatformLineSeparators("""Configuration 'default' in <target> does not match the consumer attributes
Configuration 'default':
  - Required key 'other' but no value provided.
  - Found will 'fail' but wasn't required.""")
    }

    def "revalidates explicit configuration selection if it has attributes"() {
        def dep = new LocalComponentDependencyMetadata(Stub(ComponentSelector), Stub(ModuleVersionSelector), "from", null, 'bar', [] as Set, [], false, false, true)
        def fromComponent = Stub(ComponentResolveMetadata)
        def fromConfig = Stub(LocalConfigurationMetadata) {
            getAttributes() >> attributes(key: 'something')
        }
        fromConfig.hierarchy >> ["from"]
        def defaultConfig = defaultConfiguration()
        def toFooConfig = Stub(LocalConfigurationMetadata) {
            getName() >> 'foo'
            getAttributes() >> attributes(key: 'something')
            isCanBeConsumed() >> true
        }
        def toBarConfig = Stub(LocalConfigurationMetadata) {
            getName() >> 'bar'
            getAttributes() >> attributes(key: 'something else')
            isCanBeConsumed() >> true
        }
        def toComponent = Stub(ComponentResolveMetadata) {
            getConsumableConfigurationsHavingAttributes() >> [toFooConfig, toBarConfig]
            getComponentId() >> Stub(ComponentIdentifier) {
                getDisplayName() >> "<target>"
            }
            getAttributesSchema() >> EmptySchema.INSTANCE
        }

        attributesSchema.attribute(Attribute.of('key', String))

        given:
        toComponent.getConfiguration("default") >> defaultConfig
        toComponent.getConfiguration("foo") >> toFooConfig
        toComponent.getConfiguration("bar") >> toBarConfig

        when:
        dep.selectConfigurations(fromComponent, fromConfig, toComponent, attributesSchema)*.name as Set

        then:
        def e = thrown(IncompatibleConfigurationSelectionException)
        e.message == toPlatformLineSeparators("""Configuration 'bar' in <target> does not match the consumer attributes
Configuration 'bar': Required key 'something' and found incompatible value 'something else'.""")
    }

    @Unroll("selects configuration '#expected' from target component with Java proximity matching strategy (#scenario)")
    def "selects the target configuration from target component with Java proximity matching strategy"() {
        def dep = new LocalComponentDependencyMetadata(Stub(ComponentSelector), Stub(ModuleVersionSelector), "from", null, null, [] as Set, [], false, false, true)
        def fromComponent = Stub(ComponentResolveMetadata)
        def fromConfig = Stub(LocalConfigurationMetadata) {
            getAttributes() >> attributes(queryAttributes)
        }
        fromConfig.hierarchy >> ["from"]
        def defaultConfig = defaultConfiguration()
        def toFooConfig = Stub(LocalConfigurationMetadata) {
            getName() >> 'foo'
            getAttributes() >> attributes(fooAttributes)
            isCanBeResolved() >> false
            isCanBeConsumed() >> true
        }
        def toBarConfig = Stub(LocalConfigurationMetadata) {
            getName() >> 'bar'
            getAttributes() >> attributes(barAttributes)
            isCanBeResolved() >> false
            isCanBeConsumed() >> true
        }
        def toComponent = Stub(ComponentResolveMetadata) {
            getConsumableConfigurationsHavingAttributes() >> [toFooConfig, toBarConfig]
            getAttributesSchema() >> attributesSchema
            getComponentId() >> Stub(ComponentIdentifier) {
                getDisplayName() >> "<target>"
            }
        }
        attributesSchema.attribute(Attribute.of('platform', JavaVersion), {
            it.ordered { a, b -> a <=> b }
            it.ordered(true, { a, b -> a <=> b })
        })
        attributesSchema.attribute(Attribute.of('flavor', String), {
            it.compatibilityRules.assumeCompatibleWhenMissing()
        })
        attributesSchema.attribute(Attribute.of('extra', String), {
            it.compatibilityRules.assumeCompatibleWhenMissing()
        })

        given:
        toComponent.getConfiguration("default") >> defaultConfig
        toComponent.getConfiguration("foo") >> toFooConfig
        toComponent.getConfiguration("bar") >> toBarConfig

        expect:
        try {
            def result = dep.selectConfigurations(fromComponent, fromConfig, toComponent, attributesSchema)*.name as Set
            if (expected == null && result) {
                throw new AssertionError("Expected an ambiguous result, but got $result")
            }
            assert result == [expected] as Set
        } catch (AmbiguousConfigurationSelectionException e) {
            if (expected == null) {
                assert e.message.startsWith(toPlatformLineSeparators("Cannot choose between the following configurations on <target>:\n  - bar\n  - foo\nAll of them match the consumer attributes:"))
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
        'Java 8 is not compatible with Java 7'                      | [platform: JavaVersion.JAVA7]                 | [platform: JavaVersion.JAVA8]                 | [:]                                           | 'default'
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
        def dep = new LocalComponentDependencyMetadata(Stub(ComponentSelector), Stub(ModuleVersionSelector), "from", null, null, [] as Set, [], false, false, true)
        def fromComponent = Stub(ComponentResolveMetadata)
        def fromConfig = Stub(LocalConfigurationMetadata) {
            getAttributes() >> attributes(queryAttributes)
        }
        fromConfig.hierarchy >> ["from"]
        def defaultConfig = defaultConfiguration()
        def toFooConfig = Stub(LocalConfigurationMetadata) {
            getName() >> 'foo'
            getAttributes() >> attributes(fooAttributes)
            isCanBeResolved() >> false
            isCanBeConsumed() >> true
        }
        def toBarConfig = Stub(LocalConfigurationMetadata) {
            getName() >> 'bar'
            getAttributes() >> attributes(barAttributes)
            isCanBeResolved() >> false
            isCanBeConsumed() >> true
        }
        def toComponent = Stub(ComponentResolveMetadata) {
            getConsumableConfigurationsHavingAttributes() >> [toFooConfig, toBarConfig]
            getAttributesSchema() >> attributesSchema
            getComponentId() >> Stub(ComponentIdentifier) {
                getDisplayName() >> "<target>"
            }
        }
        attributesSchema.attribute(Attribute.of('platform', JavaVersion), {
            it.ordered { a, b -> a <=> b }
            it.ordered(true, { a, b -> a <=> b })
        })
        attributesSchema.attribute(Attribute.of('flavor', String), {
            it.compatibilityRules.assumeCompatibleWhenMissing()
        })
        attributesSchema.attribute(Attribute.of('extra', String), {
            it.compatibilityRules.assumeCompatibleWhenMissing()
        })

        given:
        toComponent.getConfiguration("default") >> defaultConfig
        toComponent.getConfiguration("foo") >> toFooConfig
        toComponent.getConfiguration("bar") >> toBarConfig

        expect:
        try {
            def result = dep.selectConfigurations(fromComponent, fromConfig, toComponent, attributesSchema)*.name as Set
            if (expected == null && result) {
                throw new AssertionError("Expected an ambiguous result, but got $result")
            }
            assert result == [expected] as Set
        } catch (AmbiguousConfigurationSelectionException e) {
            if (expected == null) {
                assert e.message.startsWith(toPlatformLineSeparators("Cannot choose between the following configurations on <target>:\n  - bar\n  - foo\nAll of them match the consumer attributes:"))
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
        'Java 8 is not compatible with Java 7'                      | [platform: JavaVersion.JAVA7]                 | [platform: JavaVersion.JAVA8]                 | [:]                                           | 'default'
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
        def dep = new LocalComponentDependencyMetadata(Stub(ComponentSelector), Stub(ModuleVersionSelector), "from", null, "to", [] as Set, [], false, false, true)
        def fromComponent = Stub(ComponentResolveMetadata)
        def toComponent = Stub(ComponentResolveMetadata)
        def fromConfig = Stub(ConfigurationMetadata)
        fromComponent.componentId >> Stub(ComponentIdentifier) { getDisplayName() >> "thing a" }
        toComponent.componentId >> Stub(ComponentIdentifier) { getDisplayName() >> "thing b" }
        fromConfig.hierarchy >> ["from"]

        given:
        toComponent.getConfiguration("to") >> null

        when:
        dep.selectConfigurations(fromComponent, fromConfig, toComponent, attributesSchema)

        then:
        def e = thrown(ConfigurationNotFoundException)
        e.message == "Thing a declares a dependency from configuration 'from' to configuration 'to' which is not declared in the descriptor for thing b."
    }

    def "excludes nothing when no exclude rules provided"() {
        def moduleExclusions = new ModuleExclusions(new DefaultImmutableModuleIdentifierFactory())
        def dep = new LocalComponentDependencyMetadata(Stub(ComponentSelector), Stub(ModuleVersionSelector), "from", null, "to", [] as Set, [], false, false, true)

        expect:
        def exclusions = moduleExclusions.excludeAny(dep.excludes)
        exclusions == ModuleExclusions.excludeNone()
        exclusions.is(moduleExclusions.excludeAny(dep.excludes))
    }

    def "applies exclude rules when traversing the from configuration"() {
        def exclude1 = new DefaultExclude(DefaultModuleIdentifier.newId("group1", "*"))
        def exclude2 = new DefaultExclude(DefaultModuleIdentifier.newId("group2", "*"))
        def moduleExclusions = new ModuleExclusions(new DefaultImmutableModuleIdentifierFactory())
        def dep = new LocalComponentDependencyMetadata(Stub(ComponentSelector), Stub(ModuleVersionSelector), "from", null, "to", [] as Set, [exclude1, exclude2], false, false, true)

        expect:
        def exclusions = moduleExclusions.excludeAny(dep.excludes)
        exclusions == moduleExclusions.excludeAny(exclude1, exclude2)
        exclusions.is(moduleExclusions.excludeAny(dep.excludes))
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
        def dep = new LocalComponentDependencyMetadata(Stub(ComponentSelector), Stub(ModuleVersionSelector), "from", null, null, [] as Set, [], false, false, true)
        def fromComponent = Stub(ComponentResolveMetadata)
        def fromConfig = Stub(LocalConfigurationMetadata) {
            getAttributes() >> attributes(queryAttributes)
        }
        fromConfig.hierarchy >> ["from"]
        def defaultConfig = defaultConfiguration()
        def toFooConfig = Stub(LocalConfigurationMetadata) {
            getName() >> 'foo'
            getAttributes() >> attributes(key: 'something')
            isCanBeConsumed() >> true
        }
        def toBarConfig = Stub(LocalConfigurationMetadata) {
            getName() >> 'bar'
            getAttributes() >> attributes(key: 'something else')
            isCanBeConsumed() >> true
        }
        def toComponent = Stub(ComponentResolveMetadata) {
            getConsumableConfigurationsHavingAttributes() >> [toFooConfig, toBarConfig]
            getAttributesSchema() >> EmptySchema.INSTANCE
        }
        def attributeSchemaWithCompatibility = new DefaultAttributesSchema(new ComponentAttributeMatcher(), TestUtil.instantiatorFactory())
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
        dep.selectConfigurations(fromComponent, fromConfig, toComponent, attributeSchemaWithCompatibility)*.name as Set == [expected] as Set

        where:
        scenario                     | queryAttributes                 | expected
        'never compatible'           | [key: 'no match']               | 'default'
        'exact match'                | [key: 'something else']         | 'bar'
        'compatible value'           | [key: 'other']                  | 'bar'
        'wrong number of attributes' | [key: 'something', extra: 'no'] | 'default'
    }

    def configuration(String name, String... parents) {
        def config = Stub(ConfigurationMetadata)
        config.hierarchy >> ([name] as Set) + (parents as Set)
        return config
    }

    private AttributeContainer attributes(Map<String, ?> src) {
        def attributes = new DefaultMutableAttributeContainer(factory)
        src.each { String name, Object value ->
            def key = Attribute.of(name, value.class)
            attributes.attribute(key, value)
        }
        return attributes
    }

    private LocalConfigurationMetadata defaultConfiguration() {
        Stub(LocalConfigurationMetadata) {
            getName() >> 'default'
            isCanBeResolved() >> true
            isCanBeConsumed() >> true
            getAttributes() >> Mock(AttributeContainerInternal) {
                isEmpty() >> true
            }
        }
    }


    public enum JavaVersion {
        JAVA5,
        JAVA6,
        JAVA7,
        JAVA8,
        JAVA9
    }

}
