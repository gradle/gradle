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

package org.gradle.api.internal.plugins

import org.gradle.api.plugins.ExtraPropertiesExtension
import spock.lang.Specification

public class ExtraPropertiesDynamicObjectAdapterTest extends Specification {
    ExtraPropertiesExtension extension = new DefaultExtraPropertiesExtension()
    ExtraPropertiesDynamicObjectAdapter adapter =  new ExtraPropertiesDynamicObjectAdapter(String.class, extension)

    def "can get and set properties"() {
        given:
        extension.set("foo", "bar")

        expect:
        adapter.getProperty("foo") == "bar"

        when:
        adapter.setProperty("foo", "baz")

        then:
        adapter.getProperty("foo") == "baz"
        extension.foo == "baz"

        when:
        extension.foo = "bar"

        then:
        adapter.getProperty("foo") == "bar"
    }

    def "can get properties map"() {
        given:
        extension.set("p1", 1)
        extension.set("p2", 2)
        extension.set("p3", 3)

        expect:
        extension.properties == adapter.properties
    }

    def "has no methods"() {
        given:
        extension.set("foo") { }

        expect:
        !adapter.hasMethod("foo", "anything")

        and:
        !adapter.hasMethod("other")
    }

    def "can call get(name)"() {
        given:
        extension.set("foo", 12)

        expect:
        adapter.hasMethod("get", "foo")
        adapter.invokeMethod("get", ["foo"] as Object[]) == 12

        !adapter.hasMethod("get")
        !adapter.hasMethod("get", 123)
        !adapter.hasMethod("get", "foo", 12)
    }

    def "can call has(name)"() {
        given:
        extension.set("foo", 12)

        expect:
        adapter.hasMethod("has", "foo")
        adapter.invokeMethod("has", ["foo"] as Object[]) == true
        adapter.invokeMethod("has", ["bar"] as Object[]) == false

        !adapter.hasMethod("has")
        !adapter.hasMethod("has", 123)
        !adapter.hasMethod("has", "foo", 12)
    }

    def "can call set(name, value) "() {
        when:
        adapter.invokeMethod("set", ["foo", 12] as Object[])

        then:
        adapter.hasMethod("set", "foo", 12)
        extension.get("foo") == 12

        !adapter.hasMethod("set", "foo")
        !adapter.hasMethod("set", 12, "foo")
        !adapter.hasMethod("set", "foo", 12, 12)
    }

    def "getting missing property throws MPE"() {
        when:
        adapter.getProperty("foo")

        then:
        thrown(MissingPropertyException)
    }

    def "setting missing property throws MPE"() {
        when:
        adapter.setProperty("foo", 12)

        then:
        thrown(MissingPropertyException)
    }

    def "invoking method throws MME"() {
        when:
        adapter.invokeMethod("foo", "bar")

        then:
        thrown(groovy.lang.MissingMethodException)
    }
}
