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

import org.gradle.util.internal.ConfigureUtil
import spock.lang.Specification

class ConfigureByMapActionTest extends Specification {

    ConfigureByMapAction action(Object[] args) {
        new ConfigureByMapAction(*args)
    }

    def canConfigureObjectPropertyUsingMap() {
        given:
        Bean obj = new Bean()

        when:
        action(prop: 'value').execute(obj)

        then:
        obj.prop == "value"

        when:
        action(method: 'value2').execute(obj)

        then:
        obj.prop == 'value2'
    }

    def canConfigureAndValidateObjectUsingMap() {
        given:
        Bean obj = new Bean()

        when:
        action(prop: 'value', ['foo']).execute(obj)

        then:
        def e = thrown(ConfigureUtil.IncompleteInputException)
        e.missingKeys.contains("foo")

        when:
        action([prop: 'value'], ['prop']).execute(obj)

        then:
        assert obj.prop == 'value'
    }

    def canConfigureAndValidateObjectUsingMapUsingGstrings() {
        given:
        Bean obj = new Bean()
        def prop = "prop"
        def foo = "foo"

        when:
        action(["$prop": 'value'], ["$foo"]).execute(obj)

        then:
        def e = thrown(ConfigureUtil.IncompleteInputException)
        e.missingKeys.contains("foo")

        when:
        action(["$prop": 'value'], ["$prop"]).execute(obj)

        then:
        assert obj.prop == 'value'
    }

    def throwsExceptionForUnknownProperty() {
        given:
        Bean obj = new Bean()

        when:
        action(unknown: 'value').execute(obj)

        then:
        def e = thrown(MissingPropertyException)
        e.type == Bean
        e.property == 'unknown'
    }

    def "equality"() {
        expect:
        action([:]) == action([:])
        action([:], []) == action([:], [])
        action(p1: "v1") == action(p1: "v1")
        action(p1: "v1") != action(p1: "v2")
        action(p1: "v1") != action(p2: "v1")
        action(p1: "v1", ["a"]) == action(p1: "v1", ["a"])
        action(p1: "v1", ["a"]) != action(p1: "v1", ["b"])
        action(p1: "v1", ["a"]) != action(p1: "v1")
    }

}

class Bean {
    String prop
    def method(String value) {
        prop = value
    }
}
