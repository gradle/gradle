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


import org.gradle.api.provider.SetProperty
import org.gradle.util.TestUtil
import spock.lang.Specification

class SetPropertyBackedSetTest extends Specification {

    SetProperty<String> setProperty

    def setup() {
        setProperty = TestUtil.propertyFactory().setProperty(String)
    }

    def "set modification operations should be visible on backed provider"() {
        given:
        setProperty.add("first")
        Set<String> set = new SetPropertyBackedSet<>(setProperty)

        when:
        set.add("second")
        set.add("third")
        set.addAll(["forth", "fifth"])

        then:
        setProperty.get() == ["first", "second", "third", "forth", "fifth"] as Set<String>

        when:
        set.remove("third")

        then:
        setProperty.get() == ["first", "second", "forth", "fifth"] as Set<String>
        set.size() == 4

        when:
        set.add("third")

        then:
        setProperty.get() == ["first", "second", "third", "forth", "fifth"] as Set<String>
        set.size() == 5

        when:
        set.removeAll(["first", "third", "forth"])

        then:
        setProperty.get() == ["second", "fifth"] as Set<String>
        set.size() == 2

        when:
        set.addAll(["first", "third", "forth"])

        then:
        setProperty.get() == ["second", "fifth", "first", "third", "forth"] as Set<String>
        set.size() == 5

        when:
        set.retainAll(["first", "third", "forth"])

        then:
        setProperty.get() == ["first", "third", "forth"] as Set<String>
        set.size() == 3
    }

    def "set modification operations works with Groovy methods"() {
        given:
        Set<String> set = new SetPropertyBackedSet<>(setProperty)

        when:
        set.addAll(["first", "second", "third", "forth", "fifth"])

        then:
        setProperty.get() == ["first", "second", "third", "forth", "fifth"] as Set<String>

        when:
        set.removeAll { it in ["first", "third", "forth"] }

        then:
        setProperty.get() == ["second", "fifth"] as Set<String>

        when:
        set.addAll(["first", "third", "forth"])
        set.retainAll { it in ["first", "third", "forth"] }

        then:
        setProperty.get() == ["first", "third", "forth"] as Set<String>
    }

    def "contains operations work"() {
        given:
        setProperty.add("first")
        Set<String> set = new SetPropertyBackedSet<>(setProperty)

        when:
        set.add("second")
        set.add("third")
        set.addAll(["forth", "fifth"])

        then:
        set.containsAll(["first", "second", "third", "forth", "fifth"])

        when:
        set.remove("third")

        then:
        set.containsAll(["first", "second", "forth", "fifth"])
        !set.contains("third")
    }

    def "provider modifications should be visible on the set"() {
        given:
        setProperty.add("first")
        Set<String> set = new SetPropertyBackedSet<>(setProperty)

        when:
        setProperty.add("second")
        setProperty.add("third")
        setProperty.addAll(["forth", "fifth"])

        then:
        set == ["first", "second", "third", "forth", "fifth"] as Set<String>
        set.containsAll(["first", "second", "third", "forth", "fifth"])

        when:
        setProperty.set(["third", "fifth"])

        then:
        set == ["third","fifth"] as Set<String>
        set.containsAll(["third","fifth"])
        !set.contains("first")
        !set.contains("second")
        !set.contains("forth")
    }
}
