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

import org.gradle.api.artifacts.ConfigurationAttributeMatcher
import org.gradle.api.artifacts.ModuleVersionSelector
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector
import org.gradle.api.internal.artifacts.configurations.ConfigurationAttributesMatchingStrategyInternal
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions
import org.gradle.internal.component.external.descriptor.DefaultExclude
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.local.model.LocalConfigurationMetadata
import spock.lang.Specification
import spock.lang.Unroll

class LocalComponentDependencyMetadataTest extends Specification {

    private static final Map<String, Closure<Integer>> C = [
        EXACT_MATCH: { a, b -> a == b ? 0 : -1 },
        NEVER_MATCH: { a, b -> -1 },
        PREFIX_MATCH: { a, b -> b.startsWith(a) ? 0 : -1 },
        JAVA_MATCH: { a, b ->
            if (a == b) {
                return 0
            }
            def vr = Integer.valueOf(a - 'java')
            def vb = Integer.valueOf(b - 'java')
            if (vb > vr) {
                return -1
            }
            vr - vb
        }
    ]

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
            isQueryOrResolveAllowed() >> true
        }
        def toConfig = Stub(ConfigurationMetadata) {
            isConsumeOrPublishAllowed() >> true
        }
        fromConfig.hierarchy >> ["from"]

        given:
        toComponent.getConfiguration("to") >> toConfig

        expect:
        dep.selectConfigurations(fromComponent, fromConfig, toComponent) == [toConfig] as Set
    }

    @Unroll("selects configuration '#expected' from target component when all attributes are required (#comparator, #description)")
    def "selects the target configuration from target component which matches the attributes when all attributes are required"() {
        def dep = new LocalComponentDependencyMetadata(Stub(ComponentSelector), Stub(ModuleVersionSelector), "from", null, null, [] as Set, [], false, false, true)
        def strategy = Mock(ConfigurationAttributesMatchingStrategyInternal) {
            getAttributeMatcher(_) >> { attr ->
                Mock(ConfigurationAttributeMatcher) {
                    score(_, _) >> { a, b -> C[comparator](a, b) }
                }
            }
        }
        def fromComponent = Stub(ComponentResolveMetadata)
        def toComponent = Stub(ComponentResolveMetadata) {
            getConfigurationNames() >> ['foo', 'bar']
        }
        def fromConfig = Stub(LocalConfigurationMetadata) {
            getAttributes() >> queryAttributes
            getAttributeMatchingStrategy() >> strategy
        }
        fromConfig.hierarchy >> ["from"]
        def defaultConfig = Stub(LocalConfigurationMetadata) {
            getName() >> 'default'
        }
        def toFooConfig = Stub(LocalConfigurationMetadata) {
            getName() >> 'foo'
            getAttributes() >> [platform: 'java7']
            isConsumeOrPublishAllowed() >> true
        }
        def toBarConfig = Stub(LocalConfigurationMetadata) {
            getName() >> 'bar'
            getAttributes() >> [platform: 'java8']
            isConsumeOrPublishAllowed() >> true
        }

        given:
        toComponent.getConfiguration("default") >> defaultConfig
        toComponent.getConfiguration("foo") >> toFooConfig
        toComponent.getConfiguration("bar") >> toBarConfig

        expect:
        try {
            def result = dep.selectConfigurations(fromComponent, fromConfig, toComponent)*.name as Set
            if (expected == null && result) {
                throw new AssertionError("Expected an ambiguous result, but got $result")
            }
            assert result == [expected] as Set
        } catch (IllegalArgumentException e) {
            if (expected == null) {
                assert e.message.startsWith('Cannot choose between the following configurations: [bar, foo]')
            } else {
                throw e
            }
        }

        where:
        queryAttributes                     | comparator     | expected  | description
        [platform: 'java7']                 | 'EXACT_MATCH'  | 'foo'     | 'exact match on platform'
        [platform: 'java8']                 | 'EXACT_MATCH'  | 'bar'     | 'exact match on platform'
        [platform: 'native']                | 'EXACT_MATCH'  | 'default' | 'no match, fallback to default'
        [platform: 'java7', flavor: 'free'] | 'EXACT_MATCH'  | 'default' | 'flavor required and no match, fallback to default'
        [platform: 'java7']                 | 'NEVER_MATCH'  | 'default' | 'never match, fallback to default'
        [platform: 'java8']                 | 'NEVER_MATCH'  | 'default' | 'never match, fallback to default'
        [platform: 'native']                | 'NEVER_MATCH'  | 'default' | 'never match, fallback to default'
        [platform: 'java7', flavor: 'free'] | 'NEVER_MATCH'  | 'default' | 'never match, fallback to default'
        [platform: 'java']                  | 'PREFIX_MATCH' | null      | 'both configurations match'
        [platform: 'java8']                 | 'PREFIX_MATCH' | 'bar'     | 'prefix match'
        [platform: 'native']                | 'PREFIX_MATCH' | 'default' | 'no match, fallback to default'
        [platform: 'java7', flavor: 'free'] | 'PREFIX_MATCH' | 'default' | 'no match, fallback to default'
    }

    @Unroll("selects configuration '#expected' from target component with exact matching strategy (#description)")
    def "selects the target configuration from target component with exact matching strategy"() {
        def dep = new LocalComponentDependencyMetadata(Stub(ComponentSelector), Stub(ModuleVersionSelector), "from", null, null, [] as Set, [], false, false, true)
        def strategy = Mock(ConfigurationAttributesMatchingStrategyInternal) {
            getAttributeMatcher(_) >> { attr ->
                Mock(ConfigurationAttributeMatcher) {
                    score(_, _) >> { a, b -> C.EXACT_MATCH(a, b) }
                    defaultValue(_) >> { args ->
                        args[0]
                    }
                }
            }
        }
        def fromComponent = Stub(ComponentResolveMetadata)
        def toComponent = Stub(ComponentResolveMetadata) {
            getConfigurationNames() >> ['foo', 'bar']
        }
        def fromConfig = Stub(LocalConfigurationMetadata) {
            getAttributes() >> queryAttributes
            getAttributeMatchingStrategy() >> strategy
        }
        fromConfig.hierarchy >> ["from"]
        def defaultConfig = Stub(LocalConfigurationMetadata) {
            getName() >> 'default'
        }
        def toFooConfig = Stub(LocalConfigurationMetadata) {
            getName() >> 'foo'
            getAttributes() >> fooAttributes
            isConsumeOrPublishAllowed() >> true
        }
        def toBarConfig = Stub(LocalConfigurationMetadata) {
            getName() >> 'bar'
            getAttributes() >> barAttributes
            isConsumeOrPublishAllowed() >> true
        }

        given:
        toComponent.getConfiguration("default") >> defaultConfig
        toComponent.getConfiguration("foo") >> toFooConfig
        toComponent.getConfiguration("bar") >> toBarConfig

        expect:
        try {
            def result = dep.selectConfigurations(fromComponent, fromConfig, toComponent)*.name as Set
            if (expected == null && result) {
                throw new AssertionError("Expected an ambiguous result, but got $result")
            }
            assert result == [expected] as Set
        } catch (IllegalArgumentException e) {
            if (expected == null) {
                assert e.message.startsWith('Cannot choose between the following configurations: [bar, foo]')
            } else {
                throw e
            }
        }

        where:
        queryAttributes                     | fooAttributes                       | barAttributes                       | expected  | description
        [platform: 'java7']                 | [platform: 'java7']                 | [:]                                 | 'foo'     | 'exact match is found'
        [platform: 'java8']                 | [platform: 'java7']                 | [platform: 'java8']                 | 'bar'     | 'exact match is found'
        [platform: 'native']                | [platform: 'java7']                 | [:]                                 | 'default' | 'falls back on default'
        [platform: 'native']                | [platform: 'java7']                 | [platform: 'java8']                 | 'default' | 'falls back on default'
        [platform: 'java7', flavor: 'free'] | [platform: 'java7', flavor: 'paid'] | [platform: 'java7', flavor: 'free'] | 'bar'     | 'match on all attributes'
        [platform: 'java7', flavor: 'free'] | [platform: 'java7']                 | [platform: 'java7', flavor: 'free'] | null      | 'ambiguous because of default value'
        [platform: 'java7', flavor: 'free'] | [platform: 'java7']                 | [platform: 'java8']                 | 'foo'     | 'partial match'

    }


    @Unroll("selects configuration '#expected' from target component with Java proximity matching strategy (#description)")
    def "selects the target configuration from target component with Java proximity matching strategy"() {
        def dep = new LocalComponentDependencyMetadata(Stub(ComponentSelector), Stub(ModuleVersionSelector), "from", null, null, [] as Set, [], false, false, true)
        def strategy = Mock(ConfigurationAttributesMatchingStrategyInternal) {
            getAttributeMatcher(_) >> { attr ->
                Mock(ConfigurationAttributeMatcher) {
                    score(_, _) >> { a, b -> C.JAVA_MATCH(a, b) }
                }
            }
        }
        def fromComponent = Stub(ComponentResolveMetadata)
        def toComponent = Stub(ComponentResolveMetadata) {
            getConfigurationNames() >> ['foo', 'bar']
        }
        def fromConfig = Stub(LocalConfigurationMetadata) {
            getAttributes() >> queryAttributes
            getAttributeMatchingStrategy() >> strategy
        }
        fromConfig.hierarchy >> ["from"]
        def defaultConfig = Stub(LocalConfigurationMetadata) {
            getName() >> 'default'
        }
        def toFooConfig = Stub(LocalConfigurationMetadata) {
            getName() >> 'foo'
            getAttributes() >> fooAttributes
            isConsumeOrPublishAllowed() >> true
        }
        def toBarConfig = Stub(LocalConfigurationMetadata) {
            getName() >> 'bar'
            getAttributes() >> barAttributes
            isConsumeOrPublishAllowed() >> true
        }

        given:
        toComponent.getConfiguration("default") >> defaultConfig
        toComponent.getConfiguration("foo") >> toFooConfig
        toComponent.getConfiguration("bar") >> toBarConfig

        expect:
        try {
            def result = dep.selectConfigurations(fromComponent, fromConfig, toComponent)*.name as Set
            if (expected == null && result) {
                throw new AssertionError("Expected an ambiguous result, but got $result")
            }
            assert result == [expected] as Set
        } catch (IllegalArgumentException e) {
            if (expected == null) {
                assert e.message.startsWith('Cannot choose between the following configurations: [bar, foo]')
            } else {
                throw e
            }
        }

        where:
        queryAttributes     | fooAttributes                       | barAttributes                       | expected  | description
        [platform: 'java7'] | [platform: 'java7']                 | [:]                                 | 'foo'     | 'exact match is found'
        [platform: 'java8'] | [platform: 'java7']                 | [platform: 'java8']                 | 'bar'     | 'exact match is found'
        [platform: 'java8'] | [platform: 'java7']                 | [:]                                 | 'foo'     | 'Java 7  is compatible with Java 8'
        [platform: 'java8'] | [platform: 'java6']                 | [platform: 'java7']                 | 'bar'     | 'Java 7 is closer to Java 8'
        [platform: 'java7'] | [platform: 'java8']                 | [:]                                 | 'default' | 'Java 8 is not compatible with Java 7'
        [platform: 'java7'] | [platform: 'java8']                 | [platform: 'java6']                 | 'bar'     | 'Java 8 is not compatible but Java 6 is'
        [platform: 'java8'] | [platform: 'java6', flavor: 'free'] | [platform: 'java6', flavor: 'paid'] | null      | 'compatible platforms, but additional attributes unmatched'
        [platform: 'java8'] | [platform: 'java6', flavor: 'free'] | [platform: 'java7', flavor: 'paid'] | 'bar'     | 'compatible platforms, but one closer'

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
        dep.selectConfigurations(fromComponent, fromConfig, toComponent)

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

    def configuration(String name, String... parents) {
        def config = Stub(ConfigurationMetadata)
        config.hierarchy >> ([name] as Set) + (parents as Set)
        return config
    }
}
