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
import org.gradle.api.Nullable
import spock.lang.Specification

class GroovyReflectionUtilTest extends Specification {
    def "base object types are not visited"() {
        when: GroovyReflectionUtil.walkTypeHierarchy(Object, Mock(GroovyReflectionUtil.TypeVisitor))
        then: 0 * _

        when: GroovyReflectionUtil.walkTypeHierarchy(GroovyObject, Mock(GroovyReflectionUtil.TypeVisitor))
        then: 0 * _
    }

    class Base {
        @Incubating
        Object doSomething() { null }
    }

    class Child extends Base implements Serializable {
        @Nullable
        Object doSomething() { null }
    }

    def "walking type hierarchy happens breadth-first"() {
        def visitor = Mock(GroovyReflectionUtil.TypeVisitor)
        when:
        GroovyReflectionUtil.walkTypeHierarchy(Child, visitor)

        then: 1 * visitor.visitType(Child)
        then: 1 * visitor.visitType(Base)
        then: 1 * visitor.visitType(Serializable)
        then: 0 * _
    }
}
