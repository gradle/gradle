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

package org.gradle.internal.metaobject

import org.codehaus.groovy.reflection.CachedClass
import org.gradle.api.internal.coerce.MethodArgumentsTransformer
import org.gradle.api.internal.coerce.PropertySetTransformer
import spock.lang.Specification

class BeanDynamicObjectTest extends Specification {
    def "can get value of property of groovy object"() {
        def bean = new Bean(prop: "value")
        def dynamicObject = new BeanDynamicObject(bean)

        expect:
        dynamicObject.hasProperty("prop")
        dynamicObject.getProperty("prop") == "value"
    }

    def "can get value of read only property of groovy object"() {
        def bean = new Bean(prop: "value")
        def dynamicObject = new BeanDynamicObject(bean)

        expect:
        dynamicObject.hasProperty("readOnly")
        dynamicObject.getProperty("readOnly") == "read-only"
    }

    def "can get metaClass of groovy object"() {
        def bean = new Bean(prop: "value")
        def dynamicObject = new BeanDynamicObject(bean)

        expect:
        dynamicObject.hasProperty("metaClass")
        dynamicObject.getProperty("metaClass") == bean.metaClass
    }

    def "can get property of dynamic groovy object"() {
        def bean = new BeanWithDynamicProperties(prop: "value")
        def dynamicObject = new BeanDynamicObject(bean)

        expect:
        dynamicObject.getProperty("prop") == "value"
        dynamicObject.getProperty("dyno") == "ok"
    }

    def "can mix in additional readable properties by implementing PropertyMixIn"() {
        def bean = new BeanWithMixInProperties(prop: "value")
        def dynamicObject = new BeanDynamicObject(bean)

        expect:
        dynamicObject.getProperty("prop") == "value"
        dynamicObject.getProperty("dyno") == "ok"
    }

    def "can check for properties introduced using PropertyMixIn"() {
        def bean = new BeanWithMixInProperties(prop: "value")
        def dynamicObject = new BeanDynamicObject(bean)

        expect:
        dynamicObject.hasProperty("prop")
        dynamicObject.hasProperty("dyno")
        !dynamicObject.hasProperty("unknown")
    }

    def "can only check for declared properties of dynamic groovy object"() {
        def bean = new BeanWithDynamicProperties(prop: "value")
        def dynamicObject = new BeanDynamicObject(bean)

        expect:
        dynamicObject.hasProperty("prop")
        !dynamicObject.hasProperty("dyno")
    }

    def "can get property of closure delegate via closure instance"() {
        def bean = new BeanWithDynamicProperties(prop: "value")
        def cl = {}
        cl.delegate = bean
        def dynamicObject = new BeanDynamicObject(cl)

        expect:
        dynamicObject.getProperty("prop") == "value"
        dynamicObject.getProperty("dyno") == "ok"
    }

    def "fails when get value of unknown property of groovy object"() {
        def bean = new Bean(prop: "value")
        def dynamicObject = new BeanDynamicObject(bean)

        when:
        dynamicObject.getProperty("unknown")

        then:
        def e = thrown(MissingPropertyException)
        e.property == "unknown"
        e.type == Bean
        e.message == "Could not get unknown property 'unknown' for object of type ${Bean.name}."
    }

    def "fails when get value of unknown property of dynamic groovy object"() {
        def bean = new BeanWithDynamicProperties(prop: "value")
        def dynamicObject = new BeanDynamicObject(bean)

        when:
        dynamicObject.getProperty("unknown")

        then:
        def e = thrown(MissingPropertyException)
        e.property == "unknown"
        e.type == BeanWithDynamicProperties
        e.message == "Could not get unknown property 'unknown' for object of type ${BeanWithDynamicProperties.name}."
    }

    def "fails when get value of write only property of groovy object"() {
        def bean = new Bean(prop: "value")
        def dynamicObject = new BeanDynamicObject(bean)

        when:
        dynamicObject.getProperty("writeOnly")

        then:
        def e = thrown(GroovyRuntimeException)
        e.message == "Cannot get the value of write-only property 'writeOnly' for object of type ${Bean.name}."
    }

    def "does not use propertyMissing() when object implements PropertyMixIn"() {
        def bean = new BeanWithMixInProperties(prop: "value")
        def dynamicObject = new BeanDynamicObject(bean)

        when:
        dynamicObject.getProperty("unknown")

        then:
        def e = thrown(MissingPropertyException)
        e.property == "unknown"
        e.type == BeanWithMixInProperties
        e.message == "Could not get unknown property 'unknown' for object of type ${BeanWithMixInProperties.name}."

        when:
        dynamicObject.setProperty("unknown", 123)

        then:
        e = thrown(MissingPropertyException)
        e.property == "unknown"
        e.type == BeanWithMixInProperties
        e.message == "Could not set unknown property 'unknown' for object of type ${BeanWithMixInProperties.name}."
    }

    def "fails when get value of instance property of class object"() {
        def dynamicObject = new BeanDynamicObject(WithStaticFields)

        when:
        dynamicObject.getProperty("other")

        then:
        def e = thrown(MissingPropertyException)
        e.property == "other"
        e.type == Class
        e.message == "Could not get unknown property 'other' for class ${WithStaticFields.name} of type ${Class.name}."
    }

    def "fails when get value of property of dynamic groovy object and no dynamic requested"() {
        def bean = new BeanWithDynamicProperties(prop: "value")
        def dynamicObject = new BeanDynamicObject(bean).withNotImplementsMissing()

        expect:
        dynamicObject.getProperty("prop") == "value"

        when:
        dynamicObject.getProperty("dyno")

        then:
        def e = thrown(MissingPropertyException)
        e.property == "dyno"
        e.type == BeanWithDynamicProperties
        e.message == "Could not get unknown property 'dyno' for object of type ${BeanWithDynamicProperties.name}."
    }

    def "includes toString() of bean in property get error message when has custom implementation"() {
        def bean = new Bean() {
            @Override
            String toString() {
                return "<bean>"
            }
        }
        def dynamicObject = new BeanDynamicObject(bean)

        when:
        dynamicObject.getProperty("unknown")

        then:
        def e = thrown(MissingPropertyException)
        e.property == "unknown"
        e.type == bean.getClass()
        e.message == "Could not get unknown property 'unknown' for <bean> of type ${bean.getClass().name}."
    }

    def "can set value of property of groovy object"() {
        def bean = new Bean()
        def dynamicObject = new BeanDynamicObject(bean)

        when:
        dynamicObject.setProperty("prop", "value")

        then:
        bean.prop == "value"
    }

    def "can set value of write only property of groovy object"() {
        def bean = new Bean()
        def dynamicObject = new BeanDynamicObject(bean)

        expect:
        dynamicObject.hasProperty("writeOnly")

        when:
        dynamicObject.setProperty("writeOnly", "value")

        then:
        bean.prop == "value"
    }

    def "can set property of dynamic groovy object"() {
        def bean = new BeanWithDynamicProperties(prop: "value")
        def dynamicObject = new BeanDynamicObject(bean)

        when:
        dynamicObject.setProperty("dyno", "value")

        then:
        noExceptionThrown()
    }

    def "can mix in additional writable properties by implementing PropertyMixIn"() {
        def bean = new BeanWithMixInProperties(prop: "value")
        def dynamicObject = new BeanDynamicObject(bean)

        expect:
        dynamicObject.setProperty("prop", "value")
        dynamicObject.setProperty("dyno", "new")
    }

    def "can set property of closure delegate via closure instance"() {
        def bean = new BeanWithDynamicProperties()
        def cl = {}
        cl.delegate = bean
        def dynamicObject = new BeanDynamicObject(cl)

        when:
        dynamicObject.setProperty("prop", "value")

        then:
        bean.prop == "value"
    }

    def "can set value of property of groovy object when getter has different type to setter"() {
        def bean = new Bean()
        def dynamicObject = new BeanDynamicObject(bean)

        when:
        dynamicObject.setProperty("count", "abc")

        then:
        bean.count == 3
    }

    def "applies default groovy type conversions when setting property"() {
        def bean = new Bean()
        def dynamicObject = new BeanDynamicObject(bean)

        when:
        dynamicObject.setProperty("prop", "${"a".toUpperCase()}")

        then:
        bean.prop == "A"
    }

    def "can set value of property with getter and field"() {
        def bean = new Bean()
        def dynamicObject = new BeanDynamicObject(bean)

        expect:
        dynamicObject.hasProperty("fieldProp")

        when:
        dynamicObject.setProperty("fieldProp", "value")

        then:
        bean.fieldProp == "value"
    }

    def "coerces provided value when setting property"() {
        def bean = new EnumBean()
        def dynamicObject = new BeanDynamicObject(bean, EnumBean, true, false, new SomeEnumConverter(), new SomeEnumConverter())

        when:
        dynamicObject.setProperty("prop", "A")

        then:
        bean.prop == SomeEnum.A

        when:
        dynamicObject.setProperty("other", "A")

        then:
        bean.prop == SomeEnum.A

        when:
        dynamicObject.setProperty("other", 1)

        then:
        bean.prop == SomeEnum.B

        when:
        dynamicObject.setProperty("other", SomeEnum.C)

        then:
        bean.prop == SomeEnum.C

        when:
        dynamicObject.setProperty("someField", 2)

        then:
        bean.someField == SomeEnum.C
    }

    def "can get and set static property using instance"() {
        def dynamicObject = new BeanDynamicObject(new WithStaticFields())

        expect:
        dynamicObject.hasProperty("prop")
        dynamicObject.setProperty("prop", "value")
        dynamicObject.getProperty("prop") == "value"

        dynamicObject.hasProperty("other")
    }

    def "can get and set static property using class object"() {
        def dynamicObject = new BeanDynamicObject(WithStaticFields.class)

        expect:
        dynamicObject.hasProperty("prop")
        dynamicObject.setProperty("prop", "value")
        dynamicObject.getProperty("prop") == "value"

        !dynamicObject.hasProperty("other")
    }

    def "can get properties of class object"() {
        def dynamicObject = new BeanDynamicObject(WithStaticFields.class)

        expect:
        dynamicObject.hasProperty("classLoader")
        dynamicObject.getProperty("classLoader") == WithStaticFields.classLoader
    }

    def "fails when set value of unknown property of groovy object"() {
        def bean = new Bean()
        def dynamicObject = new BeanDynamicObject(bean)

        when:
        dynamicObject.setProperty("unknown", "value")

        then:
        def e = thrown(MissingPropertyException)
        e.property == "unknown"
        e.type == Bean
        e.message == "Could not set unknown property 'unknown' for object of type ${Bean.name}."
    }

    def "fails when set value of read only property of groovy object"() {
        def bean = new Bean()
        def dynamicObject = new BeanDynamicObject(bean)

        expect:
        dynamicObject.hasProperty("readOnly")

        when:
        dynamicObject.setProperty("readOnly", "value")

        then:
        def e = thrown(GroovyRuntimeException)
        e.message == "Cannot set the value of read-only property 'readOnly' for object of type ${Bean.name}."
    }

    def "fails when set value of instance property of class object"() {
        def dynamicObject = new BeanDynamicObject(WithStaticFields)

        when:
        dynamicObject.setProperty("other", "value")

        then:
        def e = thrown(MissingPropertyException)
        e.property == "other"
        e.type == Class
        e.message == "Could not set unknown property 'other' for class ${WithStaticFields.name} of type ${Class.name}."
    }

    def "fails when set value of unknown property of dynamic groovy object"() {
        def bean = new BeanWithDynamicProperties()
        def dynamicObject = new BeanDynamicObject(bean)

        when:
        dynamicObject.setProperty("unknown", 12)

        then:
        def e = thrown(MissingPropertyException)
        e.property == "unknown"
        e.type == BeanWithDynamicProperties
        e.message == "Could not set unknown property 'unknown' for object of type ${BeanWithDynamicProperties.name}."
    }

    def "fails when set value of property of dynamic groovy object and no dynamic requested"() {
        def bean = new BeanWithDynamicProperties(prop: "value")
        def dynamicObject = new BeanDynamicObject(bean).withNotImplementsMissing()

        expect:
        dynamicObject.setProperty("prop", "value")

        when:
        dynamicObject.setProperty("dyno", "value")

        then:
        def e = thrown(MissingPropertyException)
        e.property == "dyno"
        e.type == BeanWithDynamicProperties
        e.message == "Could not set unknown property 'dyno' for object of type ${BeanWithDynamicProperties.name}."
    }

    def "includes toString() of bean in property set error message when has custom implementation"() {
        def bean = new Bean() {
            @Override
            String toString() {
                return "<bean>"
            }
        }
        def dynamicObject = new BeanDynamicObject(bean)

        when:
        dynamicObject.setProperty("unknown", "value")

        then:
        def e = thrown(MissingPropertyException)
        e.message == "Could not set unknown property 'unknown' for ${bean} of type ${bean.getClass().name}."
    }

    def "can get properties of map"() {
        def bean = [:]
        def dynamicObject = new BeanDynamicObject(bean)

        expect:
        dynamicObject.hasProperty("empty")
        dynamicObject.getProperty("empty")
    }

    def "can get and set entries of map as property"() {
        def bean = [:]
        def dynamicObject = new BeanDynamicObject(bean)

        expect:
        dynamicObject.getProperty("prop") == null
        dynamicObject.setProperty("prop", "ok")
        dynamicObject.getProperty("prop") == "ok"
    }

    def "can query existence of property of map"() {
        def bean = [:]
        def dynamicObject = new BeanDynamicObject(bean)

        expect:
        !dynamicObject.hasProperty("prop")
        dynamicObject.setProperty("prop", "ok")
        dynamicObject.hasProperty("prop")
    }

    def "can get entries of map as properties"() {
        def bean = [prop: 12]
        def dynamicObject = new BeanDynamicObject(bean)

        expect:
        dynamicObject.properties.keySet() == ['class', 'empty', 'prop'] as Set
        dynamicObject.properties['empty'] == false
        dynamicObject.properties.prop == 12
    }

    def "can invoke method of groovy object"() {
        def bean = new Bean()
        def dynamicObject = new BeanDynamicObject(bean)

        expect:
        dynamicObject.invokeMethod("m", [12] as Object[]) == "[13]"
        dynamicObject.invokeMethod("overlap", [Integer] as Object[]) == Class
        dynamicObject.invokeMethod("overlap", [0] as Object[]) == Number
        dynamicObject.invokeMethod("methodWithClass", [Integer] as Object[]) == Class
        dynamicObject.invokeMethod("methodWithValue", [0] as Object[]) == Number
    }

    def "can check for methods of groovy object"() {
        def bean = new Bean()
        def dynamicObject = new BeanDynamicObject(bean)

        expect:
        dynamicObject.hasMethod("m", [12] as Object[])
        !dynamicObject.hasMethod("m", [] as Object[])
        !dynamicObject.hasMethod("other", [12] as Object[])
        dynamicObject.hasMethod("overlap", [Integer] as Object[])
        dynamicObject.hasMethod("overlap", [0] as Object[])
        !dynamicObject.hasMethod("methodWithClass", [0] as Object[])
        dynamicObject.hasMethod("methodWithClass", [Integer] as Object[])
        dynamicObject.hasMethod("methodWithValue", [0] as Object[])
        !dynamicObject.hasMethod("methodWithValue", [Integer] as Object[])
    }

    def "coerces parameters of method of groovy object"() {
        def bean = new EnumBean()
        def dynamicObject = new BeanDynamicObject(bean, EnumBean, true, false, new SomeEnumConverter(), new SomeEnumConverter())

        expect:
        dynamicObject.invokeMethod("doThing", ["A"] as Object[]) == SomeEnum.A
        dynamicObject.invokeMethod("doOtherThing", ["A"] as Object[]) == SomeEnum.A
        dynamicObject.invokeMethod("doOtherThing", [SomeEnum.B] as Object[]) == SomeEnum.B
        dynamicObject.invokeMethod("doOtherThing", [2] as Object[]) == SomeEnum.C
        dynamicObject.invokeMethod("doOtherThing", ["ignore", "A"] as Object[]) == SomeEnum.A
        dynamicObject.invokeMethod("doOtherThing", ["ignore", SomeEnum.B] as Object[]) == SomeEnum.B
        dynamicObject.invokeMethod("doOtherThing", ["ignore", 2] as Object[]) == SomeEnum.C
    }

    def "can invoke method of dynamic groovy object"() {
        def bean = new BeanWithDynamicProperties(prop: "value")
        def dynamicObject = new BeanDynamicObject(bean)

        expect:
        dynamicObject.invokeMethod("dyno", [12, "a"] as Object[]) == "[12, a]"
    }

    def "groovy object can mix in additional methods by implementing MethodMixIn"() {
        def bean = new BeanWithMixInMethods(prop: "value")
        def dynamicObject = new BeanDynamicObject(bean)

        expect:
        dynamicObject.invokeMethod("dyno", [12, "a"] as Object[]) == "[12, a]"
    }

    def "can invoke static method using instance of class"() {
        def dynamicObject = new BeanDynamicObject(new WithStaticFields())

        expect:
        dynamicObject.hasMethod("thing", ["value"] as Object[])
        dynamicObject.invokeMethod("thing", ["value"] as Object[]) == "value"

        dynamicObject.hasMethod("other", ["value"] as Object[])
    }

    def "can invoke methods of class object"() {
        def dynamicObject = new BeanDynamicObject(WithStaticFields.class)

        expect:
        dynamicObject.hasMethod("asSubclass", [Object] as Object[])
        dynamicObject.invokeMethod("asSubclass", [Object] as Object[]) == WithStaticFields
    }

    def "can invoke static method using class object"() {
        def dynamicObject = new BeanDynamicObject(WithStaticFields.class)

        expect:
        dynamicObject.hasMethod("thing", ["value"] as Object[])
        dynamicObject.invokeMethod("thing", ["value"] as Object[]) == "value"

        !dynamicObject.hasMethod("other", ["value"] as Object[])
    }

    def "can check for declared methods of dynamic groovy object"() {
        def bean = new BeanWithDynamicProperties(prop: "value")
        def dynamicObject = new BeanDynamicObject(bean)

        expect:
        dynamicObject.hasMethod("thing", [12] as Object[])
        !dynamicObject.hasMethod("dyno", [12, "a"] as Object[])
    }

    def "can check for methods mixed in using MethodMixIn"() {
        def bean = new BeanWithMixInMethods(prop: "value")
        def dynamicObject = new BeanDynamicObject(bean)

        expect:
        dynamicObject.hasMethod("thing", [12] as Object[])
        dynamicObject.hasMethod("dyno", [12, "a"] as Object[])
        !dynamicObject.hasMethod("unknown", [12, "a"] as Object[])
    }

    def "can invoke method of closure delegate via closure instance"() {
        def bean = new BeanWithDynamicProperties()
        def cl = {}
        cl.delegate = bean
        def dynamicObject = new BeanDynamicObject(cl)

        expect:
        dynamicObject.invokeMethod("dyno", [12, "a"] as Object[]) == "[12, a]"
    }

    def "fails when invoke unknown method [#method(#arguments)] of groovy object"() {
        def bean = new Bean(prop: "value")
        def dynamicObject = new BeanDynamicObject(bean)
        def args = arguments as Object[]
        when:
        dynamicObject.invokeMethod(method, args)

        then:
        def e = thrown(MissingMethodException)
        assertMethodMissing(e, method, args)

        where:
        method            | arguments
        "unknown"         | [12]
        "methodWithClass" | [0]
        "methodWithValue" | [Integer]
    }

    private void assertMethodMissing(MissingMethodException e, String name, Object... arguments) {
        assert e.method == name
        assert e.type == Bean
        assert e.message == "Could not find method ${name}() for arguments ${arguments} on object of type ${Bean.name}."
    }

    def "fails when invoke instance method of class object"() {
        def dynamicObject = new BeanDynamicObject(WithStaticFields)

        when:
        dynamicObject.invokeMethod("other", ["value"] as Object[])

        then:
        def e = thrown(MissingMethodException)
        e.method == "other"
        e.type == Class
        e.message == "Could not find method other() for arguments [value] on class ${WithStaticFields.name} of type ${Class.name}."
    }

    def "fails when invoke unknown method of dynamic groovy object"() {
        def bean = new BeanWithDynamicProperties(prop: "value")
        def dynamicObject = new BeanDynamicObject(bean)

        when:
        dynamicObject.invokeMethod("unknown", [12] as Object[])

        then:
        def e = thrown(MissingMethodException)
        e.method == "unknown"
        e.type == BeanWithDynamicProperties
        e.message == "Could not find method unknown() for arguments [12] on object of type ${BeanWithDynamicProperties.name}."
    }

    def "fails when invoke method of dynamic groovy object and no dynamic requested"() {
        def bean = new BeanWithDynamicProperties(prop: "value")
        def dynamicObject = new BeanDynamicObject(bean).withNotImplementsMissing()

        expect:
        dynamicObject.invokeMethod("thing", [12] as Object[]) == "12"

        when:
        dynamicObject.invokeMethod("dyno", [] as Object[])

        then:
        def e = thrown(MissingMethodException)
        e.method == "dyno"
        e.type == BeanWithDynamicProperties
        e.message == "Could not find method dyno() for arguments [] on object of type ${BeanWithDynamicProperties.name}."
    }

    def "does not use methodMissing to locate method when type implements MethodMixIn"() {
        def bean = new BeanWithMixInMethods(prop: "value")
        def dynamicObject = new BeanDynamicObject(bean)

        when:
        dynamicObject.invokeMethod("unknown")

        then:
        def e = thrown(MissingMethodException)
        e.method == "unknown"
        e.type == BeanWithMixInMethods
        e.message == "Could not find method unknown() for arguments [] on object of type ${BeanWithMixInMethods.name}."
    }

    def "includes toString() of bean in missing method error message when has custom implementation"() {
        def bean = new Bean() {
            @Override
            String toString() {
                return "<bean>"
            }
        }
        def dynamicObject = new BeanDynamicObject(bean)

        when:
        dynamicObject.invokeMethod("unknown", [] as Object[])

        then:
        def e = thrown(MissingMethodException)
        e.method == "unknown"
        e.type == bean.getClass()
        e.message == "Could not find method unknown() for arguments [] on <bean> of type ${bean.getClass().name}."
    }

    enum SomeEnum {
        A, B, C
    }

    class SomeEnumConverter implements PropertySetTransformer, MethodArgumentsTransformer {
        @Override
        Object transformValue(Class<?> type, Object value) {
            if (type == SomeEnum && value instanceof String) {
                return SomeEnum.valueOf(value as String)
            }
            if (type == SomeEnum && value instanceof Number) {
                return SomeEnum.values()[value as int]
            }
            return value
        }

        @Override
        Object[] transform(CachedClass[] types, Object[] args) {
            def result = new Object[args.length]
            for (int i = 0; i < types.length; i++) {
                result[i] = transformValue(types[i].theClass, args[i])
                if (!types[i].theClass.isInstance(result[i])) {
                    return args
                }
            }
            return result
        }

        @Override
        boolean canTransform(Object[] args) {
            return true
        }
    }

    static class Bean {
        String prop

        String m(int l) {
            return "[${l + 1}]"
        }

        String getReadOnly() {
            return "read-only"
        }

        void setWriteOnly(String s) {
            prop = s
        }

        Number count

        Number getCount() {
            return count
        }

        void setCount(Object str) {
            count = str.toString().length()
        }

        private String fieldProp

        String getFieldProp() {
            return fieldProp
        }

        Class overlap(Class<? extends Number> c) {
            return Class
        }

        Class overlap(Number i) {
            return Number
        }

        Class methodWithClass(Class<? extends Number> c) {
            return Class
        }

        Class methodWithValue(Number i) {
            return Number
        }
    }

    static class EnumBean {
        SomeEnum prop

        void setOther(String s) {
            prop = SomeEnum.valueOf(s)
        }

        void setOther(SomeEnum e) {
            prop = e
        }

        SomeEnum doThing(SomeEnum e) {
            e
        }

        SomeEnum doOtherThing(String s) {
            SomeEnum.valueOf(s)
        }

        SomeEnum doOtherThing(SomeEnum e) {
            e
        }

        SomeEnum doOtherThing(String s, SomeEnum e) {
            e
        }

        private SomeEnum someField

        SomeEnum getSomeField() {
            return someField
        }
    }

    static class WithStaticFields {
        static String prop

        String other

        static String thing(String v) {
            return v
        }

        String other(String v) {
            return v
        }
    }

}
