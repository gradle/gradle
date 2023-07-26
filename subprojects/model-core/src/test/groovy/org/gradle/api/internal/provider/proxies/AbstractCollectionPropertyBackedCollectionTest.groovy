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

import org.gradle.api.provider.HasMultipleValues
import org.gradle.api.provider.Provider
import spock.lang.Specification

abstract class AbstractCollectionPropertyBackedCollectionTest extends Specification {

    protected abstract <T extends Collection<String>> T cast(Collection<String> collection)
    protected abstract <T extends HasMultipleValues<String> & Provider<Collection<String>>> T multiValueProperty()
    protected abstract <T extends Collection<String>> T newCollection(HasMultipleValues<String> multipleValueProperty)

    def "modification operations should be visible on a backed property"() {
        given:
        def property = multiValueProperty()
        Collection<String> collection = newCollection(property)

        when:
        property.add("first")
        collection.add("second")
        collection.add("third")
        collection.addAll(["forth", "fifth"])

        then:
        property.get() == cast(["first", "second", "third", "forth", "fifth"])

        when:
        collection.remove("third")

        then:
        property.get() == cast(["first", "second", "forth", "fifth"])
        collection.size() == 4

        when:
        collection.add("third")

        then:
        property.get() == cast(["first", "second", "forth", "fifth", "third"])
        collection.size() == 5

        when:
        collection.removeAll(["first", "third", "forth"])

        then:
        property.get() == cast(["second", "fifth"])
        collection.size() == 2

        when:
        collection.addAll(["first", "third", "forth"])

        then:
        property.get() == cast(["second", "fifth", "first", "third", "forth"])
        collection.size() == 5

        when:
        collection.retainAll(["first", "third", "forth"])

        then:
        property.get() == cast(["first", "third", "forth"])
        collection.size() == 3
    }

    def "contains operations work"() {
        given:
        def property = multiValueProperty()
        Collection<String> collection = newCollection(property)

        when:
        property.add("first")
        property.addAll(["second", "third"])
        collection.addAll(["forth", "fifth"])

        then:
        collection.containsAll(["first", "second", "third", "forth", "fifth"])

        when:
        collection.remove("third")

        then:
        collection.containsAll(["first", "second", "forth", "fifth"])
        !collection.contains("third")
    }

    def "provider modifications should be visible on a view"() {
        given:
        def property = multiValueProperty()
        Collection<String> collection = newCollection(property)

        when:
        property.add("first")
        property.add("second")
        property.add("third")
        property.addAll(["forth", "fifth"])

        then:
        collection == cast(["first", "second", "third", "forth", "fifth"])
        collection.containsAll(["first", "second", "third", "forth", "fifth"])

        when:
        property.set(["third", "fifth"])

        then:
        collection == cast(["third","fifth"])
        collection.containsAll(["third","fifth"])
        !collection.contains("first")
        !collection.contains("second")
        !collection.contains("forth")
    }

    def "modification operations works with Groovy methods"() {
        given:
        def property = multiValueProperty()
        Collection<String> collection = newCollection(property)

        when:
        collection.addAll(["first", "second", "third", "forth", "fifth"])

        then:
        property.get() == cast(["first", "second", "third", "forth", "fifth"])

        when:
        collection.removeAll { it in ["first", "third", "forth"] }

        then:
        property.get() == cast(["second", "fifth"])

        when:
        collection.addAll(["first", "third", "forth"])
        collection.retainAll { it in ["first", "third", "forth"] }

        then:
        property.get() == cast(["first", "third", "forth"])
    }
}
