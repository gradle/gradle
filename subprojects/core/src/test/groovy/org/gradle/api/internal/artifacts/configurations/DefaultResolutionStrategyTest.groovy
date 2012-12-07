/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.api.internal.artifacts.configurations;


import org.gradle.api.Action
import org.gradle.api.artifacts.DependencyResolveDetails
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector

/**
 * by Szczepan Faber, created at: 11/2/11
 */
public class DefaultResolutionStrategyTest extends Specification {

    def strategy = new DefaultResolutionStrategy()

    def "allows setting forced modules"() {
        expect:
        strategy.forcedModules.empty

        when:
        strategy.force 'org.foo:bar:1.0', 'org.foo:baz:2.0'

        then:
        def versions = strategy.forcedModules as List
        versions.size() == 2

        versions[0].group == 'org.foo'
        versions[0].name == 'bar'
        versions[0].version == '1.0'

        versions[1].group == 'org.foo'
        versions[1].name == 'baz'
        versions[1].version == '2.0'
    }

    def "allows replacing forced modules"() {
        given:
        strategy.force 'org.foo:bar:1.0'

        when:
        strategy.forcedModules = ['hello:world:1.0', [group:'g', name:'n', version:'1']]

        then:
        def versions = strategy.forcedModules as List
        versions.size() == 2
        versions[0].group == 'hello'
        versions[1].group == 'g'
    }

    def "provides no op resolve action when no actions or forced modules configured"() {
        given:
        def details = Mock(DependencyResolveDetails)

        when:
        strategy.dependencyResolveAction.execute(details)

        then:
        0 * details._
    }

    def "provides dependency resolve action that forces modules"() {
        given:
        strategy.force 'org:bar:1.0', 'org:foo:2.0'
        def details = Mock(DependencyResolveDetails)

        when:
        strategy.dependencyResolveAction.execute(details)

        then:
        1 * details.getRequested() >> newSelector("org", "foo", "1.0")
        1 * details.forceVersion("2.0")
        0 * details._
    }

    def "provides dependency resolve action that orderly aggregates user specified actions"() {
        given:
        strategy.eachDependency({ it.forceVersion("1.0") } as Action)
        strategy.eachDependency({ it.forceVersion("2.0") } as Action)
        def details = Mock(DependencyResolveDetails)

        when:
        strategy.dependencyResolveAction.execute(details)

        then:
        1 * details.forceVersion("1.0")
        then:
        1 * details.forceVersion("2.0")
        0 * details._
    }

    def "provides dependency resolve action with forced modules first and then user specified actions"() {
        given:
        strategy.force 'org:bar:1.0', 'org:foo:2.0'
        strategy.eachDependency({ it.forceVersion("5.0") } as Action)
        strategy.eachDependency({ it.forceVersion("6.0") } as Action)

        def details = Mock(DependencyResolveDetails)

        when:
        strategy.dependencyResolveAction.execute(details)

        then: //forced modules:
        1 * details.requested >> newSelector("org", "foo", "1.0")
        1 * details.forceVersion("2.0")

        then: //user actions, in order:
        1 * details.forceVersion("5.0")
        then:
        1 * details.forceVersion("6.0")
        0 * details._
    }
}
