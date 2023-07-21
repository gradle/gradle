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


import org.gradle.api.provider.ListProperty
import org.gradle.util.TestUtil
import spock.lang.Specification

class ListPropertyBackedListTest extends Specification {

    ListProperty<String> listProperty

    def setup() {
        listProperty = TestUtil.propertyFactory().listProperty(String)
    }

    def "list modification operations should be visible on backed provider"() {
        given:
        listProperty.add("first")
        List<String> list = new ListPropertyBackedList<>(listProperty)

        when:
        list.add("second")
        list.add("third")
        list.addAll(["forth", "fifth"])

        then:
        listProperty.get() == ["first", "second", "third", "forth", "fifth"]

        when:
        list.remove("third")

        then:
        listProperty.get() == ["first", "second", "forth", "fifth"]
        list.size() == 4

        when:
        list.add(2, "third")

        then:
        listProperty.get() == ["first", "second", "third", "forth", "fifth"]
        list.size() == 5

        when:
        list.remove(3)

        then:
        listProperty.get() == ["first", "second", "third", "fifth"]
        list.size() == 4

        when:
        list.removeAll(["first", "third"])

        then:
        listProperty.get() == ["second", "fifth"]
        list.size() == 2

        when:
        list.addAll(["first", "third", "forth"])

        then:
        listProperty.get() == ["second", "fifth", "first", "third", "forth"]
        list.size() == 5

        when:
        list.retainAll(["first", "third", "forth"])

        then:
        listProperty.get() == ["first", "third", "forth"]
        list.size() == 3
    }

    def "list modification operations works with Groovy methods"() {
        given:
        List<String> list = new ListPropertyBackedList<>(listProperty)

        when:
        list.addAll(["first", "second", "third", "forth", "fifth"])

        then:
        listProperty.get() == ["first", "second", "third", "forth", "fifth"]

        when:
        list.removeAll { it in ["first", "third", "forth"] }

        then:
        listProperty.get() == ["second", "fifth"]

        when:
        list.addAll(["first", "third", "forth"])
        list.retainAll { it in ["first", "third", "forth"] }

        then:
        listProperty.get() == ["first", "third", "forth"]
    }

    def "contains operations work"() {
        given:
        listProperty.add("first")
        List<String> list = new ListPropertyBackedList<>(listProperty)

        when:
        list.add("second")
        list.add("third")
        list.addAll(["forth", "fifth"])

        then:
        list.containsAll(["first", "second", "third", "forth", "fifth"])

        when:
        list.remove("third")

        then:
        list.containsAll(["first", "second", "forth", "fifth"])
        !list.contains("third")
    }

    def "provider modifications should be visible on the list"() {
        given:
        listProperty.add("first")
        List<String> list = new ListPropertyBackedList<>(listProperty)

        when:
        listProperty.add("second")
        listProperty.add("third")
        listProperty.addAll(["forth", "fifth"])

        then:
        list == ["first", "second", "third", "forth", "fifth"]
        list.containsAll(["first", "second", "third", "forth", "fifth"])

        when:
        listProperty.set(["third", "fifth"])

        then:
        list == ["third","fifth"]
        list.containsAll(["third","fifth"])
        !list.contains("first")
        !list.contains("second")
        !list.contains("forth")
    }
}
