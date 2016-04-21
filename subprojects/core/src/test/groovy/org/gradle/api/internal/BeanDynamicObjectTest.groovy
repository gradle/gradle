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

package org.gradle.api.internal

import spock.lang.Specification

class BeanDynamicObjectTest extends Specification {
    def "can get value of property of groovy object"() {
        def bean = new Bean(prop: "value")
        def dynamicObject = new BeanDynamicObject(bean)

        expect:
        dynamicObject.getProperty("prop") == "value"
    }

    def "can get metaClass of groovy object"() {
        def bean = new Bean(prop: "value")
        def dynamicObject = new BeanDynamicObject(bean)

        expect:
        dynamicObject.getProperty("metaClass") == bean.metaClass
    }

    def "can get property of dynamic groovy object"() {
        def bean = new BeanWithDynamicProperties(prop: "value")
        def dynamicObject = new BeanDynamicObject(bean)

        expect:
        dynamicObject.getProperty("prop") == "value"
        dynamicObject.getProperty("dyno") == "ok"
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
        e.message == "Could not get unknown property 'unknown' for ${bean}."
    }

    def "fails when get value of unknown property of dynamic groovy object"() {
        def bean = new BeanWithDynamicProperties(prop: "value")
        def dynamicObject = new BeanDynamicObject(bean)

        when:
        dynamicObject.getProperty("unknown")

        then:
        def e = thrown(MissingPropertyException)
        e.message == "Could not get unknown property 'unknown' for ${bean}."
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
        e.message == "Could not get unknown property 'dyno' for ${bean}."
    }

    def "can set value of property of groovy object"() {
        def bean = new Bean()
        def dynamicObject = new BeanDynamicObject(bean)

        when:
        dynamicObject.setProperty("prop", "value")

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

    def "fails when set value of unknown property of groovy object"() {
        def bean = new Bean()
        def dynamicObject = new BeanDynamicObject(bean)

        when:
        dynamicObject.setProperty("unknown", "value")

        then:
        def e = thrown(MissingPropertyException)
        e.message == "Could not set unknown property 'unknown' for ${bean}."
    }

    def "fails when set value of unknown property of dynamic groovy object"() {
        def bean = new BeanWithDynamicProperties()
        def dynamicObject = new BeanDynamicObject(bean)

        when:
        dynamicObject.setProperty("unknown", 12)

        then:
        def e = thrown(MissingPropertyException)
        e.message == "Could not set unknown property 'unknown' for ${bean}."
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
        e.message == "Could not set unknown property 'dyno' for ${bean}."
    }

    static class Bean {
        String prop
    }
}
