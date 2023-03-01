/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.instantiation.generator

import com.google.common.base.Function
import org.gradle.api.Action
import org.gradle.api.NonExtensible
import org.gradle.api.internal.HasConvention
import org.gradle.api.internal.IConventionAware
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Property
import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import org.gradle.internal.BiAction
import org.gradle.internal.Describables
import org.gradle.internal.instantiation.PropertyRoleAnnotationHandler
import org.gradle.util.internal.ConfigureUtil
import spock.lang.Issue

import java.util.function.BiFunction

import static AsmBackedClassGeneratorTest.Bean
import static AsmBackedClassGeneratorTest.InterfaceBean
import static org.gradle.internal.instantiation.generator.AsmBackedClassGeneratorTest.*

class AsmBackedClassGeneratorDecoratedTest extends AbstractClassGeneratorSpec {
    ClassGenerator generator = AsmBackedClassGenerator.decorateAndInject([], Stub(PropertyRoleAnnotationHandler), [], new TestCrossBuildInMemoryCacheFactory(), 0)

    def "mixes in toString() implementation for class"() {
        given:
        def bean = create(Bean, Describables.of("<display name>"))
        def beanWithNoName = create(Bean)

        expect:
        bean.toString() == "<display name>"
        bean.hasUsefulDisplayName()
        bean.modelIdentityDisplayName.displayName == "<display name>"

        beanWithNoName.toString().startsWith("${Bean.name}_Decorated@")
        !beanWithNoName.hasUsefulDisplayName()
        beanWithNoName.modelIdentityDisplayName == null
    }

    def "does not mixes in toString() implementation for class that defines an implementation"() {
        given:
        def bean = create(HasToString, Describables.of("<display name>"))
        def beanWithNoName = create(HasToString)

        expect:
        bean.toString() == "<bean>"
        bean.hasUsefulDisplayName()
        bean.modelIdentityDisplayName.displayName == "<display name>"

        beanWithNoName.toString() == "<bean>"
        beanWithNoName.hasUsefulDisplayName()
        beanWithNoName.modelIdentityDisplayName == null
    }

    def "mixes in toString() implementation for interface"() {
        given:
        def bean = create(InterfaceBean, Describables.of("<display name>"))
        def beanWithNoName = create(InterfaceBean)

        expect:
        bean.toString() == "<display name>"
        bean.hasUsefulDisplayName()
        bean.modelIdentityDisplayName.displayName == "<display name>"

        beanWithNoName.toString().startsWith("${InterfaceBean.name}_Decorated@")
        !beanWithNoName.hasUsefulDisplayName()
        beanWithNoName.modelIdentityDisplayName == null
    }

    def "constructor can use toString() implementation"() {
        given:
        def bean = create(UsesToStringInConstructor, Describables.of("<display name>"))
        def beanWithNoName = create(UsesToStringInConstructor)

        expect:
        bean.name == "<display name>"

        beanWithNoName.name.startsWith("${UsesToStringInConstructor.name}_Decorated@")
    }

    def "assigns display name to read only property of type Property<T>"() {
        given:
        def finalReadOnlyBean = create(HasReadOnlyFinalProperty, Describables.of("<display name>"))
        def readOnlyBean = create(HasReadOnlyProperty, Describables.of("<display name>"))
        def readOnlyBeanWithMapping = create(HasReadOnlyProperty, Describables.of("<display name>"))
        readOnlyBeanWithMapping.conventionMapping.map("other") { "ignore" }
        def finalBeanWithOverloads = create(HasReadOnlyFinalBooleanPropertyWithOverloads, Describables.of("<display name>"))
        def beanWithOverloads = create(HasReadOnlyBooleanPropertyWithOverloads, Describables.of("<display name>"))
        def mutableBean = create(HasMutableProperty, Describables.of("<display name>"))

        expect:
        finalReadOnlyBean.someValue.toString() == "<display name> property 'someValue'"
        readOnlyBean.someValue.toString() == "<display name> property 'someValue'"
        readOnlyBeanWithMapping.someValue.toString() == "<display name> property 'someValue'"
        finalBeanWithOverloads.getSomeValue().toString() == "<display name> property 'someValue'"
        beanWithOverloads.getSomeValue().toString() == "<display name> property 'someValue'"

        // Does not assign display name to mutable property
        mutableBean.someValue.toString() == "property(java.lang.String, undefined)"
    }

    def "assigns display name to read only non-final nested property that is not managed"() {
        def bean = create(NestedBeanClass)
        def beanWithDisplayName = create(NestedBeanClass, Describables.of("<display-name>"))

        expect:
        bean.filesBean.toString() == "property 'filesBean'"
        bean.finalProp.toString().startsWith("${InterfacePropertyBean.name}_Decorated@")
        bean.mutableProperty.toString().startsWith("${InterfacePropertyBean.name}_Decorated@")

        beanWithDisplayName.filesBean.toString() == "<display-name> property 'filesBean'"
        beanWithDisplayName.finalProp.toString().startsWith("${InterfacePropertyBean.name}_Decorated@")
        beanWithDisplayName.mutableProperty.toString().startsWith("${InterfacePropertyBean.name}_Decorated@")
    }

    def "assigns display name to read only final non-managed property of type Property"() {
        def bean = create(FinalReadOnlyNonManagedPropertyBean)
        def beanWithDisplayName = create(FinalReadOnlyNonManagedPropertyBean, Describables.of("<display-name>"))

        expect:
        bean.prop.toString() == "property 'prop'"
        beanWithDisplayName.prop.toString() == "<display-name> property 'prop'"
    }

    def "can attach nested extensions to object"() {
        given:
        def bean = create(Bean)
        def e1 = bean.extensions.create('one', Bean)
        def e2 = e1.extensions.create('two', Bean)

        expect:
        bean.one.is(e1)
        bean.one.two.is(e2)
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
        def e = thrown(MissingMethodException)
        e.method == "foo"

        when:
        thing1.onPropertyMissingGet = { new Object().bar }
        conf(thing1) { abc }

        then:
        e = thrown(MissingPropertyException)
        e.property == "bar"

        when:
        thing1.onPropertyMissingSet = { name, value -> new Object().baz = true }
        conf(thing1) { abc = true }

        then:
        e = thrown(MissingPropertyException)
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
        tester.lastArgs.first() instanceof Action

        when:
        tester.twoArgs("1") { assert it == "subject" }

        then:
        tester.lastMethod == "twoArgs"
        tester.lastArgs.size() == 2
        tester.lastArgs.first() == "1"
        tester.lastArgs.last() instanceof Action

        when:
        tester.threeArgs("1", "2") { assert it == "subject" }

        then:
        tester.lastMethod == "threeArgs"
        tester.lastArgs.size() == 3
        tester.lastArgs.first() == "1"
        tester.lastArgs[1] == "2"
        tester.lastArgs.last() instanceof Action

        when:
        tester.overloaded("1") { assert it == "subject" }

        then:
        tester.lastMethod == "overloaded"
        tester.lastArgs.size() == 2
        tester.lastArgs.first() == "1"
        tester.lastArgs.last() instanceof Action

        when:
        tester.overloaded(1) { assert it == "subject" }

        then:
        tester.lastMethod == "overloaded"
        tester.lastArgs.size() == 2
        tester.lastArgs.first() == 1
        tester.lastArgs.last() instanceof Action

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
        tester.lastArgs.last() instanceof Action
        tester.twoArgsReturnsString("foo", {}) == "string"
        tester.lastArgs.last() instanceof Action
        tester.oneActionReturnsInt({}) == 1
        tester.lastArgs.last() instanceof Action
        tester.twoArgsReturnsInt("foo", {}) == 1
        tester.lastArgs.last() instanceof Action
        tester.oneActionReturnsArray({}) == [] as Object[]
        tester.lastArgs.last() instanceof Action
        tester.twoArgsReturnsArray("foo", {}) == [] as Object[]
        tester.lastArgs.last() instanceof Action
    }

    def "property set method can take an action"() {
        given:
        def bean = create(ActionMethodWithSameNameAsProperty)
        bean.prop = "value"

        when:
        bean.prop { assert it == "value" }

        then:
        bean.prop == "called"
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
        thrown IllegalArgumentException

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

    def "class can implement interface methods using Groovy property"() {
        when:
        def i = create(ImplementsInterface)
        i.prop = "prop"

        then:
        i.prop == "prop"
    }

    def "property missing implementation is invoked exactly once, with actual value"() {
        given:
        def thing = create(DynamicThing)
        def values = []
        thing.onPropertyMissingSet = { n, v -> values << v }

        when:
        thing.foo = "bar"

        then:
        values == ["bar"]
    }

    def "action methods with wide parameters are generated properly"() {
        given:
        def tester = create(ActionsTester)

        when:
        tester.actionWithLongParameter(1L) { assert it == "subject" }

        then:
        tester.lastMethod == "actionWithLongParameter"
        tester.lastArgs.size() == 2
        tester.lastArgs.first() == 1L
        tester.lastArgs.last() instanceof Action

        when:
        tester.actionWithDoubleParameter(3.14d) { assert it == "subject" }

        then:
        tester.lastMethod == "actionWithDoubleParameter"
        tester.lastArgs.size() == 2
        tester.lastArgs.first() == 3.14d
        tester.lastArgs.last() instanceof Action
    }

    def "class with wide constructor are generated properly"() {
        given:
        def reference = new Object()

        when:
        def longTester = create(HasLongConstructor, 1L, reference)

        then:
        longTester.value == 1L
        longTester.reference.is(reference)

        when:
        def doubleTester = create(HasDoubleConstructor, 3.14d, reference)

        then:
        doubleTester.value == 3.14d
        doubleTester.reference.is(reference)
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

    BiFunction<String, Object[], Object> onMethodMissing = { name, args -> methods[name] = args.toList(); null }
    Function<String, Object> onPropertyMissingGet = { name -> props[name] }
    BiAction<String, Object> onPropertyMissingSet = { name, value -> props[name] = value }

    def methodMissing(String name, args) {
        onMethodMissing.apply(name, args as Object[])
    }

    def propertyMissing(String name) {
        onPropertyMissingGet.apply(name)
    }

    def propertyMissing(String name, value) {
        onPropertyMissingSet.execute(name, value)
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

    void actionWithLongParameter(long arg, Action action) {
        lastMethod = "actionWithLongParameter"
        lastArgs = [arg, action]
        action.execute(subject)
    }

    void actionWithDoubleParameter(double arg, Action action) {
        lastMethod = "actionWithDoubleParameter"
        lastArgs = [arg, action]
        action.execute(subject)
    }
}

class ActionMethodWithSameNameAsProperty {
    String prop

    void prop(Action<String> action) {
        action.execute(prop)
        prop = "called"
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

interface WithProperties {
    String getProp()
}

class ImplementsInterface implements WithProperties {
    String prop
}

class HasReadOnlyFinalProperty {
    final Property<String> someValue

    HasReadOnlyFinalProperty(ObjectFactory objectFactory) {
        someValue = objectFactory.property(String)
    }
}

class HasReadOnlyProperty {
    String other
    private final Property<String> prop

    Property<String> getSomeValue() {
        return prop
    }

    HasReadOnlyProperty(ObjectFactory objectFactory) {
        prop = objectFactory.property(String)
    }
}

class HasReadOnlyFinalBooleanPropertyWithOverloads {
    final Property<Boolean> someValue

    boolean isSomeValue() {
        return someValue.getOrElse(false)
    }

    HasReadOnlyFinalBooleanPropertyWithOverloads(ObjectFactory objectFactory) {
        someValue = objectFactory.property(Boolean)
    }
}

class HasReadOnlyBooleanPropertyWithOverloads {
    private final Property<Boolean> prop

    boolean isSomeValue() {
        return prop.getOrElse(false)
    }

    Property<Boolean> getSomeValue() {
        return prop
    }

    HasReadOnlyBooleanPropertyWithOverloads(ObjectFactory objectFactory) {
        prop = objectFactory.property(Boolean)
    }
}

class HasMutableProperty {
    Property<String> someValue

    HasMutableProperty(ObjectFactory objectFactory) {
        someValue = objectFactory.property(String)
    }
}

class HasToString {
    @Override
    String toString() {
        return "<bean>"
    }
}

class HasLongConstructor {
    long value
    Object reference

    HasLongConstructor(long value, Object reference) {
        this.value = value
        this.reference = reference
    }
}

class HasDoubleConstructor {
    double value
    Object reference

    HasDoubleConstructor(double value, Object reference) {
        this.value = value
        this.reference = reference
    }
}
