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

import org.gradle.api.GradleException
import org.gradle.api.artifacts.ModuleVersionSelector
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.AttributeMatchingStrategy
import org.gradle.api.attributes.AttributesSchema
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.DefaultAttributeContainer
import org.gradle.api.internal.attributes.DefaultAttributeMatchingStrategy
import org.gradle.internal.component.NoMatchingConfigurationSelectionException
import org.gradle.internal.component.external.descriptor.DefaultExclude
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.local.model.LocalConfigurationMetadata
import spock.lang.Specification
import spock.lang.Unroll

class LocalComponentDependencyMetadataTest extends Specification {
    AttributeMatchingStrategy defaultMatchingStrategy
    AttributesSchema attributesSchema

    def setup() {
        defaultMatchingStrategy = new DefaultAttributeMatchingStrategy()
        attributesSchema = Mock(AttributesSchema) {
            getMatchingStrategy(_) >> defaultMatchingStrategy
            hasAttribute(_) >> { true }
        }
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
        def fromConfig = Stub(ConfigurationMetadata) {
            isCanBeResolved() >> true
        }
        def toConfig = Stub(ConfigurationMetadata) {
            isCanBeConsumed() >> true
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
        def toComponent = Stub(ComponentResolveMetadata) {
            getConfigurationNames() >> ['foo', 'bar']
        }
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
        attributesSchema.getAttributes() >> {
            [Attribute.of('key', String)]
        }
        if (allowMissing) {
            defaultMatchingStrategy.compatibilityRules.assumeCompatibleWhenMissing()
        }

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
        def toComponent = Stub(ComponentResolveMetadata) {
            getConfigurationNames() >> ['foo', 'bar']
            toString() >> 'target'
        }
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
        def schema = Mock(AttributesSchema) {
            getMatchingStrategy(_) >> new DefaultAttributeMatchingStrategy()
            hasAttribute(_) >> { true }
        }
        schema.getAttributes() >> {
            [Attribute.of('key', String)]
        }

        given:
        toComponent.getConfiguration("default") >> defaultConfig
        toComponent.getConfiguration("foo") >> toFooConfig
        toComponent.getConfiguration("bar") >> toBarConfig

        when:
        dep.selectConfigurations(fromComponent, fromConfig, toComponent, schema)*.name as Set

        then:
        def e = thrown(NoMatchingConfigurationSelectionException)
        e.message.startsWith "Unable to find a matching configuration in 'target' :"
        e.message.contains "Required key 'other' but no value provided."
        e.message.contains "Found will 'fail' but wasn't required."
    }

    def "revalidates explicit configuration selection if it has attributes"() {
        def dep = new LocalComponentDependencyMetadata(Stub(ComponentSelector), Stub(ModuleVersionSelector), "from", null, 'bar', [] as Set, [], false, false, true)
        def fromComponent = Stub(ComponentResolveMetadata)
        def toComponent = Stub(ComponentResolveMetadata) {
            getConfigurationNames() >> ['foo', 'bar']
            toString() >> 'target'
        }
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
        def schema = Mock(AttributesSchema) {
            getMatchingStrategy(_) >> new DefaultAttributeMatchingStrategy()
            hasAttribute(_) >> { true }
        }
        schema.getAttributes() >> {
            [Attribute.of('key', String)]
        }

        given:
        toComponent.getConfiguration("default") >> defaultConfig
        toComponent.getConfiguration("foo") >> toFooConfig
        toComponent.getConfiguration("bar") >> toBarConfig

        when:
        dep.selectConfigurations(fromComponent, fromConfig, toComponent, schema)*.name as Set

        then:
        def e = thrown(NoMatchingConfigurationSelectionException)
        e.message.startsWith "Unable to find a matching configuration in 'target' :"
        e.message.contains "Required key 'something' and found incompatible value 'something else'."
    }

    @Unroll("selects configuration '#expected' from target component with Java proximity matching strategy (#scenario)")
    def "selects the target configuration from target component with Java proximity matching strategy"() {
        def dep = new LocalComponentDependencyMetadata(Stub(ComponentSelector), Stub(ModuleVersionSelector), "from", null, null, [] as Set, [], false, false, true)
        def fromComponent = Stub(ComponentResolveMetadata)
        def toComponent = Stub(ComponentResolveMetadata) {
            getConfigurationNames() >> ['foo', 'bar']
            toString() >> 'target'
        }
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
        def schema = Mock(AttributesSchema) {
            getMatchingStrategy(_) >> { args ->
                def attr = args[0]
                if (attr.name == 'platform') {
                    def strategy = new DefaultAttributeMatchingStrategy()
                    strategy.with {
                        compatibilityRules.ordered { a, b -> a <=> b }
                        disambiguationRules.pickLast { a, b -> a <=> b }
                    }
                    return strategy
                }
                return defaultMatchingStrategy
            }
            hasAttribute(_) >> true
            getAttributes() >> {
                [Attribute.of('platform', JavaVersion), Attribute.of('flavor', String)]
            }
        }
        defaultMatchingStrategy.compatibilityRules.assumeCompatibleWhenMissing()

        given:
        toComponent.getConfiguration("default") >> defaultConfig
        toComponent.getConfiguration("foo") >> toFooConfig
        toComponent.getConfiguration("bar") >> toBarConfig

        expect:
        try {
            def result = dep.selectConfigurations(fromComponent, fromConfig, toComponent, schema)*.name as Set
            if (expected == null && result) {
                throw new AssertionError("Expected an ambiguous result, but got $result")
            }
            assert result == [expected] as Set
        } catch (IllegalArgumentException e) {
            if (expected == null) {
                assert e.message.startsWith("Cannot choose between the following configurations on 'target' : bar, foo. All of them match the consumer attributes:")
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
        def toComponent = Stub(ComponentResolveMetadata) {
            getConfigurationNames() >> ['foo', 'bar']
        }
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
        def schema = Mock(AttributesSchema) {
            getMatchingStrategy(_) >> { args ->
                def attr = args[0]
                if (attr.name == 'platform') {
                    def strategy = new DefaultAttributeMatchingStrategy()
                    strategy.ordered { a, b -> a <=> b }
                    return strategy
                }
                return defaultMatchingStrategy
            }
            hasAttribute(_) >> true
            getAttributes() >> {
                [Attribute.of('platform', JavaVersion), Attribute.of('flavor', String)]
            }
        }
        defaultMatchingStrategy.compatibilityRules.assumeCompatibleWhenMissing()

        given:
        toComponent.getConfiguration("default") >> defaultConfig
        toComponent.getConfiguration("foo") >> toFooConfig
        toComponent.getConfiguration("bar") >> toBarConfig

        expect:
        try {
            def result = dep.selectConfigurations(fromComponent, fromConfig, toComponent, schema)*.name as Set
            if (expected == null && result) {
                throw new AssertionError("Expected an ambiguous result, but got $result")
            }
            assert result == [expected] as Set
        } catch (IllegalArgumentException e) {
            if (expected == null) {
                assert e.message.startsWith("Cannot choose between the following configurations on 'Mock for type 'ComponentResolveMetadata' named 'toComponent'' : bar, foo. All of them match the consumer attributes:")
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
        def dep = new LocalComponentDependencyMetadata(Stub(ComponentSelector), Stub(ModuleVersionSelector), "from", null, "to", [] as Set, [], false, false, true)

        expect:
        def exclusions = dep.getExclusions(configuration("from"))
        exclusions == ModuleExclusions.excludeNone()
        exclusions.is(dep.getExclusions(configuration("other", "from")))
    }

    def "applies exclude rules when traversing the from configuration"() {
        def exclude1 = new DefaultExclude("group1", "*")
        def exclude2 = new DefaultExclude("group2", "*")
        def dep = new LocalComponentDependencyMetadata(Stub(ComponentSelector), Stub(ModuleVersionSelector), "from", null, "to", [] as Set, [exclude1, exclude2], false, false, true)

        expect:
        def exclusions = dep.getExclusions(configuration("from"))
        exclusions == ModuleExclusions.excludeAny(exclude1, exclude2)
        exclusions.is(dep.getExclusions(configuration("other", "from")))
    }

    @Unroll("can select a compatible attribute value (#scenario)")
    def "can select a compatible attribute value"() {
        def dep = new LocalComponentDependencyMetadata(Stub(ComponentSelector), Stub(ModuleVersionSelector), "from", null, null, [] as Set, [], false, false, true)
        def fromComponent = Stub(ComponentResolveMetadata)
        def toComponent = Stub(ComponentResolveMetadata) {
            getConfigurationNames() >> ['foo', 'bar']
        }
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
        def attributeSchemaWithCompatibility = Mock(AttributesSchema) {
            getMatchingStrategy(_) >> { args ->
                def strategy = new DefaultAttributeMatchingStrategy()
                strategy.with {
                    compatibilityRules.add { CompatibilityCheckDetails details ->
                        def candidate = details.producerValue
                        if (candidate == 'something') {
                            details.incompatible()
                        }
                    }
                    compatibilityRules.add { CompatibilityCheckDetails details ->
                        def requested = details.consumerValue
                        def candidate = details.producerValue
                        if (requested == candidate) { // simulate exact match
                            details.compatible()
                        }
                    }
                    compatibilityRules.add { CompatibilityCheckDetails details ->
                        def requested = details.consumerValue
                        def candidate = details.producerValue
                        if (requested == 'other' && candidate == 'something else') { // simulate compatible match
                            details.compatible()
                        }
                    }
                }
                return strategy
            }
            hasAttribute(_) >> true
            getAttributes() >> {
                [Attribute.of('key', String)]
            }
        }

        given:
        toComponent.getConfiguration("default") >> defaultConfig
        toComponent.getConfiguration("foo") >> toFooConfig
        toComponent.getConfiguration("bar") >> toBarConfig

        expect:
        dep.selectConfigurations(fromComponent, fromConfig, toComponent, attributeSchemaWithCompatibility)*.name as Set == [expected] as Set

        where:
        scenario                     | queryAttributes                 | expected
        'never compatible'           | [key: 'something']              | 'default'
        'exact match'                | [key: 'something else']         | 'bar'
        'compatible value'           | [key: 'other']                  | 'bar'
        'wrong number of attributes' | [key: 'something', extra: 'no'] | 'default'
    }

    def "matcher failure"() {
        def dep = new LocalComponentDependencyMetadata(Stub(ComponentSelector), Stub(ModuleVersionSelector), "from", null, null, [] as Set, [], false, false, true)
        def fromComponent = Stub(ComponentResolveMetadata)
        def toComponent = Stub(ComponentResolveMetadata) {
            getConfigurationNames() >> ['foo', 'bar']
        }
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

        def attributeSchemaWithCompatibility = Mock(AttributesSchema) {
            getMatchingStrategy(_) >> Mock(AttributeMatchingStrategy) {
                execute(_) >> {
                    throw new RuntimeException('oh noes!')
                }
                toString() >> 'DummyMatcher'
            }
            hasAttribute(_) >> true
        }

        given:
        toComponent.getConfiguration("default") >> defaultConfig
        toComponent.getConfiguration("foo") >> toFooConfig
        toComponent.getConfiguration("bar") >> toBarConfig

        when:
        dep.selectConfigurations(fromComponent, fromConfig, toComponent, attributeSchemaWithCompatibility)*.name as Set == [expected] as Set

        then:
        def e = thrown(GradleException)
        e.message.startsWith("Unexpected error thrown when trying to match attribute values with DummyMatcher")
    }

    def configuration(String name, String... parents) {
        def config = Stub(ConfigurationMetadata)
        config.hierarchy >> ([name] as Set) + (parents as Set)
        return config
    }

    private AttributeContainer attributes(Map<String, ?> src) {
        def attributes = new DefaultAttributeContainer()
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
