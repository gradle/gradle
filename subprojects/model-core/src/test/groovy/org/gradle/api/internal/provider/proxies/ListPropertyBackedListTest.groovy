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
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.util.TestUtil

class ListPropertyBackedListTest extends AbstractCollectionPropertyBackedCollectionTest {

    ListProperty<String> listProperty

    def setup() {
        listProperty = TestUtil.propertyFactory().listProperty(String)
    }

    @Override
    protected <T extends HasMultipleValues<String> & Provider<Collection<String>>> T multiValueProperty() {
        return listProperty
    }

    @Override
    protected <T extends Collection<String>> T cast(Collection<String> collection) {
        return collection as List<String>
    }

    @Override
    protected <T extends Collection<String>> T newCollection(HasMultipleValues<String> multipleValueProperty) {
        return new ListPropertyBackedList<>(multipleValueProperty as ListProperty<String>)
    }

    def "list specific modification operations work"() {
        given:
        def property = multiValueProperty()
        List<String> list = newCollection(property)

        when:
        property.add("first")
        list.add("second")
        list.add("third")
        list.addAll(["forth", "fifth"])

        then:
        property.get() == ["first", "second", "third", "forth", "fifth"]

        when:
        list.remove(2)

        then:
        property.get() == ["first", "second", "forth", "fifth"]
        list.size() == 4

        when:
        list.add(1, "third")

        then:
        property.get() == ["first", "third", "second", "forth", "fifth"]
        list.size() == 5

        when:
        list.removeAll(["first", "third", "forth"])

        then:
        property.get() == ["second", "fifth"]
        list.size() == 2

        when:
        list.addAll(0, ["first", "third", "forth"])

        then:
        property.get() == ["first", "third", "forth", "second", "fifth"]
        list.size() == 5

        when:
        def thirdElement = list.get(2)

        then:
        thirdElement == "forth"

        when:
        def indexOfSecond = list.indexOf("second")

        then:
        indexOfSecond == 3
    }
}
