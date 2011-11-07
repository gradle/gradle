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


import spock.lang.Specification

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
}
