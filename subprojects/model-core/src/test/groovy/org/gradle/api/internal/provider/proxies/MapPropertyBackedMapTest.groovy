/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.provider.proxies


import org.gradle.api.provider.MapProperty
import org.gradle.util.TestUtil
import spock.lang.Specification

class MapPropertyBackedMapTest extends Specification {
    MapProperty<String, String> mapProperty

    def setup() {
        mapProperty = TestUtil.propertyFactory().mapProperty(String, String)
    }

    def "map modification operations should be visible on backed provider"() {
        given:
        mapProperty.put("first", "value1")
        Map<String, String> map = new MapPropertyBackedMap<>(mapProperty)

        when:
        map.put("second", "value2")
        map.put("third", "value3")
        map.putAll(["forth": "value4", "fifth": "value5"])

        then:
        mapProperty.get() == ["first": "value1", "second": "value2", "third": "value3", "forth": "value4", "fifth": "value5"]

        when:
        map.remove("third")

        then:
        mapProperty.get() == ["first": "value1", "second": "value2", "forth": "value4", "fifth": "value5"]
        map.size() == 4

        when:
        map.put("third", "value3")

        then:
        mapProperty.get() == ["first": "value1", "second": "value2", "third": "value3", "forth": "value4", "fifth": "value5"]
        map.size() == 5

        when:
        map.replace("third", "value33")
        map.replace("second", "value22")

        then:
        mapProperty.get() == ["first": "value1", "second": "value22", "third": "value33", "forth": "value4", "fifth": "value5"]
    }

    def "map modification operations works with Groovy methods"() {
        given:
        Map<String, String> map = new MapPropertyBackedMap<>(mapProperty)

        when:
        map.putAll(["first": "value1", "second": "value2", "third": "value3", "forth": "value4", "fifth": "value5"])

        then:
        mapProperty.get() == ["first": "value1", "second": "value2", "third": "value3", "forth": "value4", "fifth": "value5"]

        when:
        map.removeAll { it.key in ["first", "third", "forth"] }

        then:
        mapProperty.get() == ["second": "value2", "fifth": "value5"]

        when:
        map.putAll(["first": "value1", "second": "value2", "third": "value3", "forth": "value4", "fifth": "value5"])
        map.retainAll { it.key in ["first", "third", "forth"] }

        then:
        mapProperty.get() == ["first": "value1", "third": "value3", "forth": "value4"]
    }

    def "contains operations work"() {
        given:
        mapProperty.put("first", "value1")
        Map<String, String> map = new MapPropertyBackedMap<>(mapProperty)

        when:
        map.put("second", "value2")
        map.put("third", "value3")

        then:
        map.containsKey("first")
        map.containsKey("second")
        map.containsKey("third")
        map.containsValue("value1")
        map.containsValue("value2")
        map.containsValue("value3")

        when:
        map.remove("third")

        then:
        map.containsKey("first")
        map.containsKey("second")
        !map.containsKey("third")
        !map.containsValue("value3")
    }

    def "provider modifications should be visible on the map"() {
        given:
        mapProperty.put("first", "value1")
        Map<String, String> map = new MapPropertyBackedMap<>(mapProperty)

        when:
        mapProperty.put("second", "value2")
        mapProperty.put("third", "value3")

        then:
        map == ["first": "value1", "second": "value2", "third": "value3"]

        when:
        mapProperty.putAll(["forth": "value4", "fifth": "value5"])

        then:
        map == ["first": "value1", "second": "value2", "third": "value3", "forth": "value4", "fifth": "value5"]
        map.containsKey("first")
        map.containsKey("second")
        map.containsKey("third")
        map.containsKey("forth")
        map.containsKey("forth")
    }
}
