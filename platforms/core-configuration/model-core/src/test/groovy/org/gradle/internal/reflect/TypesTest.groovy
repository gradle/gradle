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

package org.gradle.internal.reflect

import org.gradle.api.Incubating
import spock.lang.Specification

import javax.annotation.Nullable

class TypesTest extends Specification {
    def "base object types are not visited"() {
        when: Types.walkTypeHierarchy(Object, Mock(Types.TypeVisitor))
        then: 0 * _

        when: Types.walkTypeHierarchy(GroovyObject, [Object, GroovyObject], Mock(Types.TypeVisitor))
        then: 0 * _
    }

    class Base {
        @Incubating
        Object doSomething() { null }
    }

    interface Iface {}

    class Child extends Base implements Serializable, Iface {
        @Nullable
        Object doSomething() { null }
    }

    def "walking type hierarchy happens breadth-first"() {
        def visitor = Mock(Types.TypeVisitor)
        when:
        Types.walkTypeHierarchy(Child, [Object, GroovyObject], visitor)

        then: 1 * visitor.visitType(Child)
        then: 1 * visitor.visitType(Base)
        then: 1 * visitor.visitType(Serializable)
        then: 1 * visitor.visitType(Iface)
        then: 0 * _
    }
}
