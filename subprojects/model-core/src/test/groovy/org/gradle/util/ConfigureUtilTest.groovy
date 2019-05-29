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


import org.gradle.util.ConfigureUtil.IncompleteInputException
import spock.lang.Specification

import static org.hamcrest.CoreMatchers.equalTo
import static org.junit.Assert.assertThat

class ConfigureUtilTest extends Specification {

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
