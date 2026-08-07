/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.util

import org.gradle.util.internal.ConfigureUtil
import org.gradle.util.internal.ConfigureUtil.IncompleteInputException
import spock.lang.Requires
import spock.lang.Specification

import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.MatcherAssert.assertThat

class ConfigureUtilTest extends Specification {

    private static String privateStaticField = 'static value'
    private String privateInstanceField = 'instance value'
    private static String shadowed = 'from owner'
    private static String nestedValue = 'nested value'

    private static boolean isGroovy5OrLater() {
        GroovySystem.version.tokenize('.')[0].toInteger() >= 5
    }

    def "can read a private static field of the closure owner class"() {
        given:
        Bean obj = new Bean()

        when:
        ConfigureUtil.configure({ prop = privateStaticField }, obj)

        then:
        obj.prop == 'static value'
    }

    def "can read a private instance field of the closure owner class"() {
        given:
        Bean obj = new Bean()

        when:
        ConfigureUtil.configure({ prop = privateInstanceField }, obj)

        then:
        obj.prop == 'instance value'
    }

    def "can read a private static field declared in the closure owner super class"() {
        given:
        Bean obj = new Bean()
        def holder = new SubOfBaseWithPrivateFields()

        when:
        ConfigureUtil.configure(holder.readsStaticField(), obj)

        then:
        obj.prop == 'base static value'
    }

    @Requires({ GroovySystem.version.tokenize('.')[0].toInteger() >= 5 })
    def "can read a private instance field declared in the closure owner super class"() {
        given:
        Bean obj = new Bean()
        def holder = new SubOfBaseWithPrivateFields()

        when:
        ConfigureUtil.configure(holder.readsInstanceField(), obj)

        then:
        obj.prop == 'base instance value'
    }

    @Requires({ GroovySystem.version.tokenize('.')[0].toInteger() < 5 })
    def "cannot read a private instance field declared in the closure owner super class on Groovy 4"() {
        given:
        Bean obj = new Bean()
        def holder = new SubOfBaseWithPrivateFields()

        when:
        ConfigureUtil.configure(holder.readsInstanceField(), obj)

        then:
        thrown(MissingPropertyException)
    }

    def "delegate property shadows an owner private static field on Groovy 5"() {
        given:
        ShadowBean obj = new ShadowBean()

        when:
        ConfigureUtil.configure({ result = shadowed }, obj)

        then:
        obj.result == (groovy5OrLater ? 'from delegate' : 'from owner')
    }

    def "can read a private static field from a nested configure closure"() {
        given:
        Outer outer = new Outer()

        when:
        ConfigureUtil.configure(
            {
                inner {
                    prop = nestedValue
                }
            },
            outer
        )

        then:
        outer.inner.prop == 'nested value'
    }

    def "can read an inherited private static field from a nested configure closure"() {
        given:
        Outer outer = new Outer()
        def holder = new SubOfBaseWithPrivateFields()

        when:
        ConfigureUtil.configure(holder.nestedReadsStaticField(), outer)

        then:
        outer.inner.prop == 'base static value'
    }

    def "throws exception for an unknown property read in a closure"() {
        given:
        Bean obj = new Bean()

        when:
        ConfigureUtil.configure({ method(unknownThing) }, obj)

        then:
        def e = thrown(MissingPropertyException)
        e.property == 'unknownThing'
        e.type == Bean
    }

    def "throws exception for an unknown property read in a nested configure closure"() {
        given:
        Outer outer = new Outer()

        when:
        ConfigureUtil.configure(
            {
                inner {
                    prop = unknownThing
                }
            },
            outer
        )

        then:
        def e = thrown(MissingPropertyException)
        e.property == 'unknownThing'
    }

    def "user-thrown MissingPropertyException is not masked as an unknown property"() {
        given:
        Bean obj = new Bean()
        def holder = new SubOfBaseWithThrowingPropertyMissing()

        when:
        ConfigureUtil.configure(holder.readsMissing(), obj)

        then:
        def e = thrown(MissingPropertyException)
        e.property == 'somethingMissing'
        e.type == ForeignBean
    }

    static abstract class BaseWithPrivateFields {
        private static String baseStaticField = 'base static value'
        private String baseInstanceField = 'base instance value'

        Closure readsStaticField() {
            return { prop = baseStaticField }
        }

        Closure readsInstanceField() {
            return { prop = baseInstanceField }
        }

        Closure nestedReadsStaticField() {
            return {
                inner {
                    prop = baseStaticField
                }
            }
        }
    }

    static class SubOfBaseWithPrivateFields extends BaseWithPrivateFields {}

    static class ShadowBean {
        String shadowed = 'from delegate'
        String result
    }

    static class Outer {
        Inner inner = new Inner()

        def inner(Closure closure) {
            ConfigureUtil.configure(closure, inner)
        }
    }

    static class Inner {
        String prop
    }

    def doesNothingWhenNullClosureIsProvided() {
        given:
        def obj = []

        when:
        ConfigureUtil.configure(null, obj)
        def action = ConfigureUtil.configureUsing(null)
        action.execute(obj)
        ConfigureUtil.configureSelf(null, obj)

        then:
        obj.empty
    }

    def canConfigureObjectUsingClosure() {
        given:
        List obj = []
        def cl = {
            add('a');
            assertThat(size(), equalTo(1));
            assertThat(obj, equalTo(['a']))
        }

        when:
        ConfigureUtil.configure(cl, obj)

        then:
        obj == ['a']
    }

    def passesConfiguredObjectToClosureAsParameter() {
        given:
        List obj = []
        def cl = {
            it.is obj
        }
        def cl2 = {List list ->
            list.is obj
        }
        def cl3 = {->
            delegate.is obj
        }

        when:
        ConfigureUtil.configure(cl, obj)
        ConfigureUtil.configure(cl2, obj)
        ConfigureUtil.configure(cl3, obj)

        then:
        noExceptionThrown()
    }

    def canConfigureObjectPropertyUsingMap() {
        given:
        Bean obj = new Bean()

        when:
        ConfigureUtil.configureByMap(obj, prop: 'value')

        then:
        obj.prop == "value"

        when:
        ConfigureUtil.configureByMap(obj, method: 'value2')

        then:
        obj.prop == 'value2'
    }

    def canConfigureAndValidateObjectUsingMap() {
        given:
        Bean obj = new Bean()

        when:
        ConfigureUtil.configureByMap([prop: 'value'], obj, ['foo'])

        then:
        def e = thrown(IncompleteInputException)
        e.missingKeys.contains("foo")

        when:
        ConfigureUtil.configureByMap([prop: 'value'], obj, ['prop'])

        then:
        assert obj.prop == 'value'
    }

    def canConfigureAndValidateObjectUsingMapUsingGstrings() {
        given:
        Bean obj = new Bean()
        def prop = "prop"
        def foo = "foo"

        when:
        ConfigureUtil.configureByMap(["$prop": 'value'], obj, ["$foo"])

        then:
        def e = thrown(IncompleteInputException)
        e.missingKeys.contains("foo")

        when:
        ConfigureUtil.configureByMap(["$prop": 'value'], obj, ["$prop"])

        then:
        assert obj.prop == 'value'
    }

    def throwsExceptionForUnknownProperty() {
        given:
        Bean obj = new Bean()

        when:
        ConfigureUtil.configureByMap(obj, unknown: 'value')

        then:
        def e = thrown(MissingPropertyException)
        e.type == Bean
        e.property == 'unknown'
    }

    static class TestConfigurable implements Configurable {
        def props = [:]

        TestConfigurable configure(Closure closure) {
            props.with(closure)
            this
        }
    }

    def testConfigurableAware() {
        given:
        def c = new TestConfigurable()

        when:
        ConfigureUtil.configure({ a = 1 }, c)

        then:
        c.props.a == 1
    }

    def createsActionThatCanConfigureObjects() {
        given:
        def c = new TestConfigurable()
        def b = new Bean()

        when:
        def action = ConfigureUtil.configureUsing { prop = "p" }
        action.execute(c)
        action.execute(b)

        then:
        c.props.prop == "p"
        b.prop == "p"
    }

    def createsIsolatedActionThatCanConfigureObjects() {
        given:
        def c = new TestConfigurable()
        def b = new Bean()

        when:
        def action = ConfigureUtil.configureUsingIsolatedAction { prop = "p" }
        action.execute(c)
        action.execute(b)

        then:
        c.props.prop == "p"
        b.prop == "p"
    }

    void configureByMapTriesMethodForExtensibleObjects() {
        given:
        Bean bean = TestUtil.instantiatorFactory().decorateLenient().newInstance(Bean)

        when:
        ConfigureUtil.configureByMap(bean, method: "foo")

        then:
        bean.prop == "foo"
    }

    static class Bean {
        String prop
        def method(String value) {
            prop = value
        }
    }
}

class ForeignBean {}

abstract class BaseWithThrowingPropertyMissing {
    def propertyMissing(String name) {
        throw new MissingPropertyException(name, ForeignBean)
    }

    Closure readsMissing() {
        return { prop = somethingMissing }
    }
}

class SubOfBaseWithThrowingPropertyMissing extends BaseWithThrowingPropertyMissing {}
