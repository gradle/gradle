/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model.internal.manage.schema.extract

import org.gradle.api.Incubating
import org.gradle.api.Nullable
import org.gradle.model.Managed
import spock.lang.Specification

@SuppressWarnings("GroovyPointlessBoolean")
class ModelSchemaUtilsTest extends Specification {
    def "base object types have no candidate methods"() {
        expect:
        ModelSchemaUtils.getCandidateMethods(Object).isEmpty()
        ModelSchemaUtils.getCandidateMethods(GroovyObject).isEmpty()
    }

    def "base object types are not visited"() {
        when: ModelSchemaUtils.walkTypeHierarchy(Object, Mock(ModelSchemaUtils.TypeVisitor))
        then: 0 * _

        when: ModelSchemaUtils.walkTypeHierarchy(GroovyObject, Mock(ModelSchemaUtils.TypeVisitor))
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
        def visitor = Mock(ModelSchemaUtils.TypeVisitor)
        when:
        ModelSchemaUtils.walkTypeHierarchy(Child, visitor)

        then: 1 * visitor.visitType(Child)
        then: 1 * visitor.visitType(Base)
        then: 1 * visitor.visitType(Serializable)
        then: 0 * _

    }

    def "overridden methods retain annotations"() {
        when:
        def methods = ModelSchemaUtils.getCandidateMethods(Child)

        then:
        methods.keySet() == (["doSomething"] as Set)
        methods.values()*.name == ["doSomething", "doSomething"]
        methods.values()*.declaringClass == [Child, Base]
        methods.values()*.declaredAnnotations.flatten()*.annotationType() == [Nullable, Incubating]
    }

    @Managed
    abstract class ManagedType  {
        abstract String getValue()
        abstract void setValue(String value)
    }

    def "detects managed property"() {
        expect:
        ModelSchemaUtils.isMethodDeclaredInManagedType(ModelSchemaUtils.getCandidateMethods(ManagedType).get("getValue")) == true
    }

    class UnmanagedType  {
        String value
    }

    def "detects unmanaged property"() {
        expect:
        ModelSchemaUtils.isMethodDeclaredInManagedType(ModelSchemaUtils.getCandidateMethods(UnmanagedType).get("getValue")) == false
    }
}
