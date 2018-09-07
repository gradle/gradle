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
import org.gradle.internal.UncheckedException
import spock.lang.Specification

import java.lang.annotation.Inherited
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

import static org.gradle.internal.reflect.JavaReflectionUtil.*

class JavaReflectionUtilTest extends Specification {
    JavaTestSubject myProperties = new JavaTestSubject()

    def "property names"() {
        expect:
        propertyNames(new JavaTestSubject()) == ['myBooleanProperty', 'myOtherBooleanProperty', 'myProperty', 'myProperty2', 'myProperty3', 'protectedProperty', 'writeOnly', 'multiValue'] as Set

        and:
        propertyNames(new JavaTestSubjectSubclass()) == ['myBooleanProperty', 'myOtherBooleanProperty', 'myProperty', 'myProperty2', 'myProperty3', 'protectedProperty', 'writeOnly', 'multiValue', 'subclassBoolean'] as Set

        and:
        propertyNames(new WithProperties()) == ['metaClass', 'prop1', 'prop2', 'something', 'somethingElse', 'writeOnly'] as Set
    }

    def "read property"() {
        expect:
        readableProperty(JavaTestSubject, String, "myProperty").getValue(myProperties) == "myValue"
    }

    def "read property using instance"() {
        expect:
        readableProperty(myProperties, String, "myProperty").getValue(myProperties) == "myValue"
    }

    def "read field" () {
        expect:
        readableField(JavaTestSubject, String, "myField").getValue(myProperties) == "myFieldValue"
    }

    def "read field using instance" () {
        expect:
        readableField(myProperties, String, "myField").getValue(myProperties) == "myFieldValue"
    }

    def "write property"() {
        when:
        writeableProperty(JavaTestSubject, "myProperty", String.class).setValue(myProperties, "otherValue")

        then:
        readableProperty(JavaTestSubject, String, "myProperty").getValue(myProperties) == "otherValue"
    }

    def "write property with multiple setters"() {
        when:
        writeableProperty(JavaTestSubject, "myProperty2", String.class).setValue(myProperties, "stringValue")

        then:
        readableProperty(JavaTestSubject, String, "myProperty2").getValue(myProperties) == "stringValue"

        when:
        writeableProperty(JavaTestSubject, "myProperty2", File.class).setValue(myProperties, new File("fileValue"))

        then:
        readableProperty(JavaTestSubject, String, "myProperty2").getValue(myProperties) == "fileValue"
    }

    def "picks the generic object setter if the typed setter does not match the value type"() {
        when:
        def property = writeableProperty(JavaTestSubject, "myProperty", File.class)

        then:
        property.type == Object.class
    }

    def "picks the typed setter if it is the better match"() {
        when:
        def property = writeableProperty(JavaTestSubject, "myProperty", String.class)

        then:
        property.type == String.class
    }

    def "picks the best matching typed setter"() {
        when:
        def property = writeableProperty(JavaTestSubject, "myProperty3", Arrays.asList("foo", "bar").class)

        then:
        property.type == Collection.class

        when:
        property = writeableProperty(JavaTestSubject, "myProperty3", "bar".class)

        then:
        property.type == CharSequence.class

        when:
        property = writeableProperty(JavaTestSubject, "myProperty3", int.class)

        then:
        property.type == Object.class
    }

    def "picks the generic iterable setter if the typed setter does not match the value type"() {
        when:
        def property = writeableProperty(JavaTestSubject, "multiValue", List.class)

        then:
        property.type == Iterable.class
    }

    def "can handle null as property type"() {
        when:
        writeableProperty(JavaTestSubject, "myProperty", null)

        then:
        //we do not know which 'myProperty' setter is picked, as both fit equally well
        noExceptionThrown()
    }

    def "cannot write primitive type properties if type is unknown"() {
        when:
        writeableProperty(JavaTestSubject, "myBooleanProperty", null)

        then:
        def e = thrown(NoSuchPropertyException)
        e.message == "Could not find setter method for property 'myBooleanProperty' accepting null value on class JavaTestSubject."
    }

    def "read boolean property"() {
        expect:
        readableProperty(JavaTestSubject, Boolean, "myBooleanProperty").getValue(myProperties) == true
    }

    def "read boolean field" () {
        expect:
        readableField(JavaTestSubject, Boolean, "myBooleanField").getValue(myProperties) == true
    }

    def "set boolean field" () {
        when:
        writeableField(JavaTestSubject, "myBooleanField").setValue(myProperties, false)

        then:
        readableField(JavaTestSubject, Boolean, "myBooleanField").getValue(myProperties) == false
    }

    def "cannot set value on non public fields"(){
        when:
        writeableField(JavaTestSubject, "myBooleanProperty").setValue(myProperties, false)

        then:
        thrown(NoSuchPropertyException);
    }

    def "write boolean property"() {
        when:
        writeableProperty(JavaTestSubject, "myBooleanProperty", Boolean.class).setValue(myProperties, false)

        then:
        readableProperty(JavaTestSubject, Boolean, "myBooleanProperty").getValue(myProperties) == false
    }

    def "cannot read property that doesn't have a well formed getter"() {
        when:
        readableProperty(JavaTestSubject, String, property)

        then:
        def e = thrown NoSuchPropertyException
        e.message == "Could not find getter method for property '${property}' on class JavaTestSubject."

        where:
        property              | _
        "doesNotExist"        | _
        "notABooleanProperty" | _
        "staticProperty"      | _
        "paramProperty"       | _
        "voidProperty"        | _
        "writeOnly"           | _
    }

    def "cannot read property that is not public"() {
        when:
        readableProperty(JavaTestSubject, String, property)

        then:
        def e = thrown NoSuchPropertyException
        e.message == "Could not find getter method for property '${property}' on class JavaTestSubject."

        where:
        property            | _
        "privateProperty"   | _
        "protectedProperty" | _
    }

    def "cannot write property that doesn't have a well formed setter"() {
        when:
        writeableProperty(JavaTestSubject, property, Object.class)

        then:
        def e = thrown NoSuchPropertyException
        e.message == "Could not find setter method for property '${property}' of type Object on class JavaTestSubject."

        where:
        property                 | _
        "doesNotExist"           | _
        "myOtherBooleanProperty" | _
        "staticProperty"         | _
        "paramProperty"          | _
    }

    def "cannot write property that is not public"() {
        when:
        writeableProperty(JavaTestSubject, property, Object.class)

        then:
        def e = thrown NoSuchPropertyException
        e.message == "Could not find setter method for property '${property}' of type Object on class JavaTestSubject."

        where:
        property            | _
        "privateProperty"   | _
        "protectedProperty" | _
    }

    def "call methods successfully reflectively"() {
        expect:
        method(myProperties.class, String, "getMyProperty").invoke(myProperties) == myProperties.myProp
        method(myProperties.class, String, "doSomeStuff", int.class, Integer.class).invoke(myProperties, 1, 2) == "1.2"

        when:
        method(myProperties.class, Void, "setMyProperty", String).invoke(myProperties, "foo")

        then:
        method(myProperties.class, String, "getMyProperty").invoke(myProperties) == "foo"
    }

    def "call static methods successfully reflectively" () {
        when:
        staticMethod(myProperties.class, Void, "setStaticProperty", String.class).invokeStatic("foo")

        then:
        staticMethod(myProperties.class, String, "getStaticProperty").invokeStatic() == "foo"
    }

    def "static methods are identifiable" () {
        expect:
        staticMethod(myProperties.class, Void, "setStaticProperty", String.class).isStatic()
        staticMethod(myProperties.class, String, "getStaticProperty").isStatic()
        method(myProperties.class, String, "getMyProperty").isStatic() == false
    }

    def "call failing methods reflectively"() {
        when:
        method(myProperties.class, Void, "throwsException").invoke(myProperties)

        then:
        def e = thrown IllegalStateException
        e == myProperties.failure

        when:
        method(myProperties.class, Void, "throwsCheckedException").invoke(myProperties)

        then:
        def checkedFailure = thrown UncheckedException
        checkedFailure.cause instanceof JavaTestSubject.TestCheckedException
        checkedFailure.cause.cause == myProperties.failure
    }

    def "call declared method that may not be public"() {
        expect:
        method(JavaTestSubjectSubclass, String, "protectedMethod").invoke(new JavaTestSubjectSubclass()) == "parent"
        method(JavaTestSubjectSubclass, String, "overridden").invoke(new JavaTestSubjectSubclass()) == "subclass"
    }

    def "cannot call unknown method"() {
        when:
        method(JavaTestSubjectSubclass, String, "unknown")

        then:
        def e = thrown NoSuchMethodException
        e.message == /Could not find method unknown() on JavaTestSubjectSubclass./
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

    static class Thing {
        final String name

        Thing(String name) {
            this.name = name
        }

        Thing() {
            this(null)
        }
    }

    def "default toString methods"() {
        expect:
        hasDefaultToString(clazz)

        where:
        clazz << [new Object(), new Root()]
    }

    def "should not have a default toString"() {
        expect:
        !hasDefaultToString(new ClassWithToString())
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

class ClassWithToString {
    @Override
    public String toString() {
        return "ClassWithToString{}";
    }
}

class WithProperties {
    String prop1
    boolean prop2

    void setWriteOnly(String s1) { }

    Boolean isSomething() { return null }

    boolean isSomethingElse() { return true }

    String isNotAThing() { "no" }

    private String getPrivateThing() { null }
}
