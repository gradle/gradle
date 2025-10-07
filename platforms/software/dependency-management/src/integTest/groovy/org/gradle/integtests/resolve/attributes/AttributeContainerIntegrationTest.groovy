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

import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.internal.artifacts.JavaEcosystemSupport
import org.gradle.integtests.fixtures.AbstractIntegrationSpec


class AttributeContainerIntegrationTest extends AbstractIntegrationSpec {
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

    def "can use addAllLater in Kotlin"() {
        buildKotlinFile << """
            val color = Attribute.of("color", String::class.java)
            val shape = Attribute.of("shape", String::class.java)

            val foo = configurations.create("foo").attributes
            foo.attribute(color, "green")

            val bar = configurations.create("bar").attributes
            bar.attribute(color, "red")
            bar.attribute(shape, "square")
            assert(bar.getAttribute(color) == "red")    // `color` is originally red

            bar.addAllLater(foo)
            assert(bar.getAttribute(color) == "green")  // `color` gets overwritten
            assert(bar.getAttribute(shape) == "square") // `shape` does not

            foo.attribute(color, "purple")
            bar.getAttribute(color) == "purple"         // addAllLater is lazy

            bar.attribute(color, "orange")
            assert(bar.getAttribute(color) == "orange") // `color` gets overwritten again
            assert(bar.getAttribute(shape) == "square") // `shape` remains the same
        """

        expect:
        succeeds("help")
    }

    // In Gradle 10, we can simply let these usages "pass through" without error/special handling.
    // The constants have since been removed from the Usage class.
    // This deprecation acts as our final warning to stop using these in build logic.
    def "declaring legacy usage attribute is deprecated"() {
        buildFile << """
            configurations {
                create("custom")  {
                    attributes {
                        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, "${legacyUsage}"))
                    }
                }
            }
        """

        expect:
        executer.expectDocumentedDeprecationWarning("Declaring a Usage attribute with a legacy value has been deprecated. This will fail with an error in Gradle 10. A Usage attribute was declared with value '${legacyUsage}'. Declare a Usage attribute with value '${replacedUsage}' and a LibraryElements attribute with value '${replacedLibraryElements}' instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecate_legacy_usage_values")
        succeeds("help")

        where:
        legacyUsage                                            | replacedUsage      | replacedLibraryElements
        JavaEcosystemSupport.DEPRECATED_JAVA_API_JARS          | Usage.JAVA_API     | LibraryElements.JAR
        JavaEcosystemSupport.DEPRECATED_JAVA_API_CLASSES       | Usage.JAVA_API     | LibraryElements.CLASSES
        JavaEcosystemSupport.DEPRECATED_JAVA_RUNTIME_JARS      | Usage.JAVA_RUNTIME | LibraryElements.JAR
        JavaEcosystemSupport.DEPRECATED_JAVA_RUNTIME_CLASSES   | Usage.JAVA_RUNTIME | LibraryElements.CLASSES
        JavaEcosystemSupport.DEPRECATED_JAVA_RUNTIME_RESOURCES | Usage.JAVA_RUNTIME | LibraryElements.RESOURCES
    }
}
