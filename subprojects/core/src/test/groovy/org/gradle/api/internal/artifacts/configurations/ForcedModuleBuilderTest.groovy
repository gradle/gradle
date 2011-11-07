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


import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.internal.artifacts.configurations.ForcedModuleBuilder.InvalidDependencyFormat
import spock.lang.Specification

/**
 * by Szczepan Faber, created at: 10/14/11
 */
public class ForcedModuleBuilderTest extends Specification {

    def "understands gav notation"() {
        when:
        def v = new ForcedModuleBuilder().build("org.foo:bar:1.0") as List

        then:
        v.size() == 1
        v[0].group == 'org.foo'
        v[0].name  == 'bar'
        v[0].version  == '1.0'
    }

    def "allows exact type on input"() {
        ModuleIdentifier id = ForcedModuleBuilder.identifier("org.foo", "bar", "2.0")

        when:
        def v = new ForcedModuleBuilder().build(id) as List

        then:
        v.size() == 1
        v[0].group == 'org.foo'
        v[0].name  == 'bar'
        v[0].version  == '2.0'
    }

    def "allows list of objects on input"() {
        ModuleIdentifier id = ForcedModuleBuilder.identifier("org.foo", "bar", "2.0")

        when:
        def v = new ForcedModuleBuilder().build([id, ["hey:man:1.0"], [group:'i', name:'like', version:'maps']]) as List

        then:
        v.size() == 3
        v[0].name == 'bar'
        v[1].name == 'man'
        v[2].name == 'like'
    }

    def "allows map on input"() {
        when:
        def v = new ForcedModuleBuilder().build([group: 'org.foo', name: 'bar', version:'1.0']) as List

        then:
        v.size() == 1
        v[0].group == 'org.foo'
        v[0].name  == 'bar'
        v[0].version  == '1.0'
    }

    def "fails for unknown types"() {
        //TODO SF
    }

    def "reports missing keys for map notation"() {
        //TODO SF
    }

    def "reports invalid format for string notation"() {
        when:
        new ForcedModuleBuilder().build(["org.foo:bar1.0"])

        then:
        thrown(InvalidDependencyFormat)
    }
}
