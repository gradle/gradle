/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal

import org.gradle.internal.reflect.DirectInstantiator
import spock.lang.Specification
import spock.lang.Issue

class AsmBackedClassGeneratorGroovyTest extends Specification {

    def generator = new AsmBackedClassGenerator()
    def instantiator = new ClassGeneratorBackedInstantiator(generator, new DirectInstantiator())

    private <T> T create(Class<T> clazz, Object... args) {
        instantiator.newInstance(clazz, *args)
    }

    @Issue("GRADLE-2417")
    def "can use dynamic object as closure delegate"() {
        given:
        def thing = create(DynamicThing)
        def closure = {
            m1(1,2,3)
            p1 = 1
            p1 = p1 + 1
        }

        and:
        closure.delegate = thing
        closure.resolveStrategy = Closure.DELEGATE_FIRST

        when:
        closure()

        then:
        thing.methods.size() == 1
        thing.props.p1 == 2
    }
}

class DynamicThing {
    def methods = [:]
    def props = [:]

    def methodMissing(String name, args) {
        methods[name] = args.toList()
    }

    def propertyMissing(String name) {
        props[name]
    }

    def propertyMissing(String name, value) {
        props[name] = value
    }
}
