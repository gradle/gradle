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

package org.gradle.internal.reflect

import org.gradle.api.specs.Spec
import org.gradle.api.specs.Specs
import org.gradle.internal.UncheckedException
import spock.lang.Specification

import java.lang.annotation.Inherited
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

import static org.gradle.internal.reflect.JavaReflectionUtil.*

class JavaReflectionUtilTest extends Specification {
    JavaTestSubject myProperties = new JavaTestSubject()

    def "read property"() {
        expect:
        JavaReflectionUtil.readProperty(myProperties, "myProperty") == "myValue"
    }

    def "write property"() {
        when:
        JavaReflectionUtil.writeProperty(myProperties, "myProperty", "otherValue")

        then:
        JavaReflectionUtil.readProperty(myProperties, "myProperty") == "otherValue"
    }

    def "read boolean property"() {
        expect:
        JavaReflectionUtil.readProperty(myProperties, "myBooleanProperty") == true
    }

    def "write boolean property"() {
        when:
        JavaReflectionUtil.writeProperty(myProperties, "myBooleanProperty", false)

        then:
        JavaReflectionUtil.readProperty(myProperties, "myBooleanProperty") == false
    }

    def "read property that doesn't exist"() {
        when:
        JavaReflectionUtil.readProperty(myProperties, "unexisting")

        then:
        UncheckedException e = thrown()
        e.cause instanceof NoSuchMethodException
    }

    def "write property that doesn't exist"() {
        when:
        JavaReflectionUtil.writeProperty(myProperties, "unexisting", "someValue")

        then:
        UncheckedException e = thrown()
        e.cause instanceof NoSuchMethodException
    }

    def "call methods successfully reflectively"() {
        expect:
        method(myProperties.class, String, "getMyProperty").invoke(myProperties) == myProperties.myProp

        when:
        method(myProperties.class, Void, "setMyProperty", String).invoke(myProperties, "foo")

        then:
        method(myProperties.class, String, "getMyProperty").invoke(myProperties) == "foo"
    }

    def "call failing methods reflectively"() {
        when:
        method(myProperties.class, Void, "throwsException").invoke(myProperties)

        then:
        thrown IllegalStateException
    }

    def "call declared method that may not be public"() {
        expect:
        method(JavaTestSubjectSubclass, String, "protectedMethod").invoke(new JavaTestSubjectSubclass()) == "parent"
        method(JavaTestSubjectSubclass, String, "overridden").invoke(new JavaTestSubjectSubclass()) == "subclass"
    }

    def "find all methods"() {
        given:
        def stringMethods = String.getDeclaredMethods().toList()
        def objectMethods = Object.getDeclaredMethods().toList()
        def allMethods = stringMethods + objectMethods - objectMethods.findAll { objMeth ->
            stringMethods.find { it.name == objMeth.name && it.parameterTypes == objMeth.parameterTypes }
        }

        expect:
        findAllMethods(String, Specs.satisfyAll()) == allMethods
        findAllMethods(String, { it.name == "toString" } as Spec) == stringMethods.findAll { it.name == "toString" }
    }

    def "find method"() {
        expect:
        findMethod(String, { it.name == "toString" } as Spec) == String.declaredMethods.find { it.name == "toString" }
        findMethod(String, { it.name == "getClass" } as Spec) == Object.declaredMethods.find { it.name == "getClass" }
    }

    def "get annotation"() {
        expect:
        getAnnotation(Root, InheritedAnnotation).value() == "default"
        getAnnotation(Subclass, InheritedAnnotation).value() == "default"
        getAnnotation(RootInterface, InheritedAnnotation).value() == "default"
        getAnnotation(SubInterface, InheritedAnnotation).value() == "default"

        getAnnotation(Root, NotInheritedAnnotation).value() == "default"
        getAnnotation(Subclass, NotInheritedAnnotation) == null
        getAnnotation(RootInterface, NotInheritedAnnotation).value() == "default"
        getAnnotation(SubInterface, NotInheritedAnnotation) == null

        getAnnotation(ImplementsRootInterface, InheritedAnnotation).value() == "default"
        getAnnotation(ImplementsRootInterface, NotInheritedAnnotation) == null
        getAnnotation(ImplementsSubInterface, InheritedAnnotation).value() == "default"
        getAnnotation(ImplementsSubInterface, NotInheritedAnnotation) == null
        getAnnotation(ImplementsBoth, InheritedAnnotation).value() == "default"
        getAnnotation(ImplementsBoth, NotInheritedAnnotation) == null

        getAnnotation(OverrideFirst, InheritedAnnotation).value() == "HasAnnotations"
        getAnnotation(OverrideLast, InheritedAnnotation).value() == "default"

        getAnnotation(InheritsInterface, InheritedAnnotation).value() == "default"
        getAnnotation(InheritsInterface, NotInheritedAnnotation) == null
    }

}

@Retention(RetentionPolicy.RUNTIME)
@Inherited
@interface InheritedAnnotation {
    String value() default "default"
}

@Retention(RetentionPolicy.RUNTIME)
@interface NotInheritedAnnotation {
    String value() default "default"
}

@InheritedAnnotation
@NotInheritedAnnotation
class Root {}

class Subclass extends Root {}

@InheritedAnnotation
@NotInheritedAnnotation
interface RootInterface {}

interface SubInterface extends RootInterface {}

class ImplementsRootInterface implements RootInterface {}
class ImplementsSubInterface implements SubInterface {}
class ImplementsBoth implements RootInterface, SubInterface {}

@InheritedAnnotation(value = "HasAnnotations")
interface HasAnnotations {}

class OverrideFirst implements HasAnnotations, RootInterface, SubInterface {}
class OverrideLast implements RootInterface, SubInterface, HasAnnotations {}

class SuperWithInterface implements RootInterface {}
class InheritsInterface extends SuperWithInterface {}