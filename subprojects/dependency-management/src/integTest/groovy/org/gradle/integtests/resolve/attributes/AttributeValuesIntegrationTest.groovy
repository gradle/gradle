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

package org.gradle.integtests.resolve.attributes

import org.gradle.integtests.fixtures.AbstractIntegrationSpec


class AttributeValuesIntegrationTest extends AbstractIntegrationSpec {
    def "cannot use an attribute value that cannot be made isolated - #type"() {
        given:
        buildFile << """
    class Thing implements Named {
        String name
    }
    def attr = Attribute.of($type)

    configurations {
        broken
    }
    configurations.broken.attributes.attribute(attr, $value)
"""

        when:
        fails()

        then:
        failure.assertHasCause("Could not isolate value ")
        failure.assertHasCause("Could not serialize value of type ")

        where:
        type      | value
        "Thing"   | "new Thing(name: 'broken')"
        "Project" | "project"
        "List"    | "[{}]"
    }

    def "can use attribute value that can be made isolated - #type"() {
        given:
        buildFile << """
    interface Flavor extends Named { }
    def attr = Attribute.of($type)

    configurations {
        ok
    }
    configurations.ok.attributes.attribute(attr, $value)
    configurations.ok.files.each { println it }
"""

        expect:
        succeeds()

        where:
        type       | value
        "Integer"  | "123"
        "Number"   | "123"
        "Object"   | "123"
        "List"     | "['string']"
        "Flavor"   | "objects.named(Flavor, 'abc')"
        "Named"    | "objects.named(Named, 'abc')"
        "Number[]" | "[1, 1.2] as Number[]"
    }

    def "attribute value is isolated from original value"() {
        given:
        buildFile << """
    class Thing implements Named, Serializable {
        String name
    }
    def attr = Attribute.of(List)

    configurations {
        ok
    }
    def value = [new Thing(name: 'a'), new Thing(name: 'b')]
    configurations.ok.attributes.attribute(attr, value)

    value[0].name = 'other'
    value.add(new Thing(name: 'c'))

    def isolated = configurations.ok.attributes.getAttribute(attr)
    assert isolated.size() == 2
    assert isolated[0].name == 'a'
    assert isolated[1].name == 'b'
"""

        expect:
        succeeds()
    }
}
