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
import org.gradle.model.Managed
import spock.lang.Specification

import javax.annotation.Nullable

class ModelSchemaUtilsTest extends Specification {
    def "base object types have no candidate methods"() {
        expect:
        ModelSchemaUtils.getCandidateMethods(Object).isEmpty()
        ModelSchemaUtils.getCandidateMethods(GroovyObject).isEmpty()
    }

    class Base {
        @Incubating
        Object doSomething() { null }
    }

    class Child extends Base implements Serializable {
        @Nullable
        Object doSomething() { null }
    }

    def "overridden methods retain annotations"() {
        when:
        def methods = ModelSchemaUtils.getCandidateMethods(Child)

        then:
        methods.methodNames() == (["doSomething"] as Set)
        methods.allMethods().values().flatten()*.name == ["doSomething", "doSomething"]
        methods.allMethods().values().flatten()*.declaringClass == [Child, Base]
        methods.allMethods().values()*.declaredAnnotations.flatten()*.annotationType() == [Nullable, Incubating]
    }

    @Managed
    abstract class ManagedType  {
        abstract String getValue()
        abstract void setValue(String value)
    }

    def "detects managed property"() {
        expect:
        ModelSchemaUtils.isMethodDeclaredInManagedType(ModelSchemaUtils.getCandidateMethods(ManagedType).methodsNamed("getValue").values().flatten())
    }

    class UnmanagedType  {
        String value
    }

    def "detects unmanaged property"() {
        expect:
        !ModelSchemaUtils.isMethodDeclaredInManagedType(ModelSchemaUtils.getCandidateMethods(UnmanagedType).methodsNamed("getValue").values().flatten())
    }

    interface TypeWithOverloadedMethods {
        String anything()
        String someOverloadedMethod(Object param)
        String someOverloadedMethod(int param)
        CharSequence someOverriddenCovariantMethod(Object param)
    }

    def "gets overridden methods from single type"() {
        expect:
        ModelSchemaUtils.getCandidateMethods(TypeWithOverloadedMethods).overriddenMethodsNamed("someOverloadedMethod").isEmpty()
    }

    def "gets overloaded methods from a single type"() {
        expect:
        def overloaded = ModelSchemaUtils.getCandidateMethods(TypeWithOverloadedMethods).overloadedMethodsNamed("someOverloadedMethod")
        overloaded.size() == 2
        overloaded.values()[0]*.name == ["someOverloadedMethod"]
        overloaded.values()[1]*.name == ["someOverloadedMethod"]
    }

    interface SubTypeWithOverloadedMethods extends TypeWithOverloadedMethods {
        @Override String someOverloadedMethod(Object param)
        @Override String someOverloadedMethod(int param)
        @Override String someOverriddenCovariantMethod(Object param)
    }

    def "gets overridden methods from type hierarchy"() {
        expect:
        def overridden = ModelSchemaUtils.getCandidateMethods(SubTypeWithOverloadedMethods).overriddenMethodsNamed("someOverloadedMethod")
        overridden.size() == 2
        overridden.values()[0].size() == 2
        overridden.values()[0]*.name == ["someOverloadedMethod", "someOverloadedMethod"]
        overridden.values()[0]*.returnType == [String, String]
        overridden.values()[0]*.parameterTypes == [[int], [int]]
        overridden.values()[1].size() == 2
        overridden.values()[1]*.name == ["someOverloadedMethod", "someOverloadedMethod"]
        overridden.values()[1]*.returnType == [String, String]
        overridden.values()[1]*.parameterTypes == [[Object], [Object]]
    }

    def "gets covariant return type overridden methods from type hierarchy"() {
        expect:
        def overridden = ModelSchemaUtils.getCandidateMethods(SubTypeWithOverloadedMethods).overriddenMethodsNamed("someOverriddenCovariantMethod")
        overridden.size() == 1
        overridden.values()[0].size() == 2
        overridden.values()[0]*.name == ["someOverriddenCovariantMethod", "someOverriddenCovariantMethod"]
        overridden.values()[0]*.parameterTypes == [[Object], [Object]]
        overridden.values()[0]*.returnType == [String, CharSequence]
    }

    def "gets overloaded methods from type hierachy"() {
        expect:
        def overloaded = ModelSchemaUtils.getCandidateMethods(SubTypeWithOverloadedMethods).overloadedMethodsNamed("someOverloadedMethod")
        overloaded.size() == 2
        overloaded.values()[0]*.name == ["someOverloadedMethod", "someOverloadedMethod"]
        overloaded.values()[1]*.name == ["someOverloadedMethod", "someOverloadedMethod"]
    }
}
