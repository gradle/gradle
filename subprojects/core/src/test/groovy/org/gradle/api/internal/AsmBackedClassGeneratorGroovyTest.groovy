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

import org.gradle.api.Action
import org.gradle.api.NonExtensible
import org.gradle.api.plugins.ExtensionAware
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.typeconversion.TypeConversionException
import org.gradle.util.ConfigureUtil
import spock.lang.Issue
import spock.lang.Specification

import javax.inject.Inject

class AsmBackedClassGeneratorGroovyTest extends Specification {

    def generator = new AsmBackedClassGenerator()
    def instantiator = new ClassGeneratorBackedInstantiator(generator, DirectInstantiator.INSTANCE)

    private <T> T create(Class<T> clazz, Object... args) {
        instantiator.newInstance(clazz, *args)
    }

    @Issue("GRADLE-2417")
    def "can use dynamic object as closure delegate"() {
        given:
        def thing = create(DynamicThing)

        when:
        conf(thing) {
            m1(1, 2, 3)
            p1 = 1
            p1 = p1 + 1
        }

        then:
        thing.methods.size() == 1
        thing.props.p1 == 2
    }

    def "unassociated missing exceptions are thrown"() {
        given:
        def thing1 = create(DynamicThing)

        when:
        thing1.onMethodMissing = { name, args -> [].foo() }
        conf(thing1) { m1() }

        then:
        def e = thrown(groovy.lang.MissingMethodException)
        e.method == "foo"

        when:
        thing1.onPropertyMissingGet = { new Object().bar }
        conf(thing1) { abc }

        then:
        e = thrown(groovy.lang.MissingPropertyException)
        e.property == "bar"

        when:
        thing1.onPropertyMissingSet = { name, value -> new Object().baz = true }
        conf(thing1) { abc = true }

        then:
        e = thrown(groovy.lang.MissingPropertyException)
        e.property == "baz"

    }

    def "any method with action as the last param is closurised"() {
        given:
        def tester = create(ActionsTester)

        when:
        tester.oneAction { assert it == "subject" }

        then:
        tester.lastMethod == "oneAction"
        tester.lastArgs.size() == 1
        tester.lastArgs.first() instanceof ClosureBackedAction

        when:
        tester.twoArgs("1") { assert it == "subject" }

        then:
        tester.lastMethod == "twoArgs"
        tester.lastArgs.size() == 2
        tester.lastArgs.first() == "1"
        tester.lastArgs.last() instanceof ClosureBackedAction

        when:
        tester.threeArgs("1", "2") { assert it == "subject" }

        then:
        tester.lastMethod == "threeArgs"
        tester.lastArgs.size() == 3
        tester.lastArgs.first() == "1"
        tester.lastArgs[1] == "2"
        tester.lastArgs.last() instanceof ClosureBackedAction

        when:
        tester.overloaded("1") { assert it == "subject" }

        then:
        tester.lastMethod == "overloaded"
        tester.lastArgs.size() == 2
        tester.lastArgs.first() == "1"
        tester.lastArgs.last() instanceof ClosureBackedAction

        when:
        tester.overloaded(1) { assert it == "subject" }

        then:
        tester.lastMethod == "overloaded"
        tester.lastArgs.size() == 2
        tester.lastArgs.first() == 1
        tester.lastArgs.last() instanceof ClosureBackedAction

        when:
        def closure = { assert it == "subject" }
        tester.hasClosure("1", closure)

        then:
        tester.lastMethod == "hasClosure"
        tester.lastArgs.size() == 2
        tester.lastArgs.first() == "1"
        tester.lastArgs.last().is(closure)

        expect: // can return values
        tester.oneActionReturnsString({}) == "string"
        tester.lastArgs.last() instanceof ClosureBackedAction
        tester.twoArgsReturnsString("foo", {}) == "string"
        tester.lastArgs.last() instanceof ClosureBackedAction
        tester.oneActionReturnsInt({}) == 1
        tester.lastArgs.last() instanceof ClosureBackedAction
        tester.twoArgsReturnsInt("foo", {}) == 1
        tester.lastArgs.last() instanceof ClosureBackedAction
        tester.oneActionReturnsArray({}) == [] as Object[]
        tester.lastArgs.last() instanceof ClosureBackedAction
        tester.twoArgsReturnsArray("foo", {}) == [] as Object[]
        tester.lastArgs.last() instanceof ClosureBackedAction
    }

    def "can coerce enum values"() {
        given:
        def i = create(EnumCoerceTestSubject)

        when:
        i.enumProperty = "abc"

        then:
        i.enumProperty == TestEnum.ABC

        when:
        i.someEnumMethod("DEF")

        then:
        i.enumProperty == TestEnum.DEF

        when:
        i.enumProperty "abc"

        then:
        i.enumProperty == TestEnum.ABC

        when:
        i.enumProperty "foo"

        then:
        thrown TypeConversionException

        when:
        i.enumMethodWithStringOverload("foo")

        then:
        i.stringValue == "foo"

        when:
        i.enumMethodWithStringOverload(TestEnum.DEF)

        then:
        i.enumProperty == TestEnum.DEF
    }

    def "can call methods during construction"() {
        /*
            We route all methods through invokeMethod, which requires fields
            added in the subclass. We have special handling for the case where
            methods are called before this field has been initialised; this tests that.
         */
        when:
        def i = create(CallsMethodDuringConstruction)

        then:
        i.setDuringConstructor == i.class
        i.setAtFieldInit == i.class
    }

    def "can use inherited properties during construction"() {
        when:
        def i = create(UsesInheritedPropertiesDuringConstruction)

        then:
        i.someValue == 'value'
    }

    def "can call private methods internally"() {
        /*
            We have to specially handle private methods in our dynamic protocol.
         */
        given:
        def i = create(CallsPrivateMethods)

        when:
        i.flagCalled("a")

        then:
        i.calledWith == String

        when:
        i.flagCalled(1.2)

        then:
        i.calledWith == Number

        when:
        i.flagCalled([])

        then:
        i.calledWith == Object

        when:
        i.flagCalled(1)

        then:
        i.calledWith == Integer
    }

    def "can use non extensible objects"() {
        def i = create(NonExtensibleObject)

        when:
        i.testEnum "ABC"

        then:
        i.testEnum == TestEnum.ABC

        !(TestEnum instanceof ExtensionAware)
        !(TestEnum instanceof IConventionAware)
        !(TestEnum instanceof HasConvention)

        when:
        i.ext.foo = "bar"

        then:
        def e = thrown(MissingPropertyException)
        e.property == "ext"
    }

    def conf(o, c) {
        ConfigureUtil.configure(c, o)
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-2863")
    def "checked exceptions from private methods are thrown"() {
        when:
        create(CallsPrivateMethods).callsPrivateThatThrowsCheckedException("1")

        then:
        thrown IOException
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-2863")
    def "private methods are called with Groovy semantics"() {
        when:
        def foo = "bar"
        def obj = create(CallsPrivateMethods)

        then:
        obj.callsPrivateStringMethodWithGString("$foo") == "BAR"
    }

    def "can inject service using a service getter method"() {
        given:
        def services = Mock(ServiceRegistry)
        def service = Mock(Runnable)
        _ * services.get(Runnable) >> service

        when:
        def obj = create(BeanWithServices, services)

        then:
        obj.thing == service
        obj.getThing() == service
        obj.getProperty("thing") == service
    }

    def "can optionally set injected service using a service setter method"() {
        given:
        def services = Mock(ServiceRegistry)
        def service = Mock(Runnable)

        when:
        def obj = create(BeanWithMutableServices, services)
        obj.thing = service

        then:
        obj.thing == service
        obj.getThing() == service
        obj.getProperty("thing") == service

        and:
        0 * services._
    }

    def "service lookup is lazy and the result is cached"() {
        given:
        def services = Mock(ServiceRegistry)
        def service = Mock(Runnable)

        when:
        def obj = create(BeanWithServices, services)

        then:
        0 * services._

        when:
        obj.thing

        then:
        1 * services.get(Runnable) >> service
        0 * services._

        when:
        obj.thing

        then:
        0 * services._
    }
}

enum TestEnum {
    ABC, DEF
}

class EnumCoerceTestSubject {
    TestEnum enumProperty

    String stringValue

    void someEnumMethod(TestEnum testEnum) {
        this.enumProperty = testEnum
    }

    void enumMethodWithStringOverload(TestEnum testEnum) {
        enumProperty = testEnum
    }

    void enumMethodWithStringOverload(String stringValue) {
        this.stringValue = stringValue
    }
}

@NonExtensible
class NonExtensibleObject {
    TestEnum testEnum
}

class DynamicThing {
    def methods = [:]
    def props = [:]

    Closure onMethodMissing = { name, args -> methods[name] = args.toList() }
    Closure onPropertyMissingGet = { name -> props[name] }
    Closure onPropertyMissingSet = { name, value -> props[name] = value }

    def methodMissing(String name, args) {
        onMethodMissing(name, args)
    }

    def propertyMissing(String name) {
        onPropertyMissingGet(name)
    }

    def propertyMissing(String name, value) {
        onPropertyMissingSet(name, value)
    }
}

class ActionsTester {

    Object subject = "subject"
    String lastMethod
    List lastArgs

    void oneAction(Action action) {
        lastMethod = "oneAction"
        lastArgs = [action]
        action.execute(subject)
    }

    void twoArgs(String first, Action action) {
        lastMethod = "twoArgs"
        lastArgs = [first, action]
        action.execute(subject)
    }

    void threeArgs(String first, String second, Action action) {
        lastMethod = "threeArgs"
        lastArgs = [first, second, action]
        action.execute(subject)
    }

    void overloaded(Integer i, Action action) {
        lastMethod = "overloaded"
        lastArgs = [i, action]
        action.execute(subject)
    }

    void overloaded(String s, Action action) {
        lastMethod = "overloaded"
        lastArgs = [s, action]
        action.execute(subject)
    }

    void hasClosure(String s, Action action) {
        lastMethod = "hasClosure"
        lastArgs = [s, action]
    }

    void hasClosure(String s, Closure closure) {
        lastMethod = "hasClosure"
        lastArgs = [s, closure]
    }

    String oneActionReturnsString(Action action) {
        lastMethod = "oneAction"
        lastArgs = [action]
        action.execute(subject)
        "string"
    }

    String twoArgsReturnsString(String first, Action action) {
        lastMethod = "twoArgs"
        lastArgs = [first, action]
        action.execute(subject)
        "string"
    }

    int oneActionReturnsInt(Action action) {
        lastMethod = "oneAction"
        lastArgs = [action]
        action.execute(subject)
        1
    }

    int twoArgsReturnsInt(String first, Action action) {
        lastMethod = "twoArgs"
        lastArgs = [first, action]
        action.execute(subject)
        1
    }

    Object[] oneActionReturnsArray(Action action) {
        lastMethod = "oneAction"
        lastArgs = [action]
        action.execute(subject)
        [] as Object[]
    }

    Object[] twoArgsReturnsArray(String first, Action action) {
        lastMethod = "twoArgs"
        lastArgs = [first, action]
        action.execute(subject)
        [] as Object[]
    }

}

class CallsMethodDuringConstruction {

    Class setAtFieldInit = getClass()
    Map<String, String> someMap = [:]
    Class setDuringConstructor

    CallsMethodDuringConstruction() {
        setDuringConstructor = setAtFieldInit
        someMap['a'] = 'b'
        assert setDuringConstructor
    }
}

class UsesInheritedPropertiesDuringConstruction extends TestJavaObject {
    UsesInheritedPropertiesDuringConstruction() {
        assert metaClass != null
        assert getMetaClass() != null
        assert metaClass.getProperty(this, "someValue") == "value"
        assert asDynamicObject.getProperty("someValue") == "value"
        assert getProperty("someValue") == "value"
        assert someValue == "value"
    }
}

class CallsPrivateMethods {

    Class calledWith

    void flagCalled(arg) {
        doFlagCalled(arg)
    }

    private doFlagCalled(String s) {
        calledWith = String
    }

    private doFlagCalled(Number s) {
        calledWith = Number
    }

    private doFlagCalled(Integer s) {
        calledWith = Integer
    }

    private doFlagCalled(Object s) {
        calledWith = Object
    }

    // It's important here that we take an untyped arg, and call a method that types a typed arg
    // See https://issues.gradle.org/browse/GRADLE-2863
    def callsPrivateThatThrowsCheckedException(s) {
        try {
            throwsCheckedException(s)
        } catch (Exception e) {
            assert e instanceof IOException
            throw e
        }
    }

    private throwsCheckedException(String a) {
        throw new IOException("!")
    }

    def callsPrivateStringMethodWithGString(GString gString) {
        upperCaser(gString)
    }

    private upperCaser(String str) {
        str.toUpperCase()
    }
}

class BeanWithServices {
    ServiceRegistry services

    BeanWithServices(ServiceRegistry services) {
        this.services = services
    }

    @Inject
    Runnable getThing() { throw new UnsupportedOperationException() }
}

class BeanWithMutableServices extends BeanWithServices {
    BeanWithMutableServices(ServiceRegistry services) {
        super(services)
    }

    void setThing(Runnable runnnable) { throw new UnsupportedOperationException() }
}
