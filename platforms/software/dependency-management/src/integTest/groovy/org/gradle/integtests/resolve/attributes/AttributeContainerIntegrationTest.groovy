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

    /**
     * Builds the exact deprecation warning that {@code Attribute.of} emits when an unsupported
     * type is used as an attribute value type.
     */
    private static String unsupportedTypeDeprecation(String typeName, String attributeName) {
        return "Using type '${typeName}' as a value type for attribute '${attributeName}' has been deprecated. " +
            "This will fail with an error in Gradle 10. " +
            "Attribute values must be of type String, Boolean, a subtype of Number, or implement org.gradle.api.Named. " +
            "Using an unsupported type may cause failures during dependency resolution, publishing, or configuration cache serialization. " +
            "Consult the upgrading guide for further information: " +
            "https://docs.gradle.org/current/userguide/upgrading_version_9.html#unsupported_attribute_value_type"
    }

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
        if (expectedDeprecation) {
            executer.expectDocumentedDeprecationWarning(expectedDeprecation)
        }
        fails()

        then:
        failure.assertHasCause("Could not isolate value ")
        failure.assertHasCause("Could not serialize value of type ")

        where:
        // Attribute.of(Class) uses the type's canonical name — with WordUtils.uncapitalize applied,
        // which only lowercases the first character of the whole string. For 'Project' and 'List'
        // that first character is already lowercase, so the attribute name ends up equal to the
        // full canonical name. Thing implements Named, so no deprecation fires for that row.
        type      | value                       | expectedDeprecation
        "Thing"   | "new Thing(name: 'broken')" | null
        "Project" | "project"                   | unsupportedTypeDeprecation("org.gradle.api.Project", "org.gradle.api.Project")
        "List"    | "[{}]"                      | unsupportedTypeDeprecation("java.util.List", "java.util.List")
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

        when:
        if (expectedDeprecation) {
            executer.expectDocumentedDeprecationWarning(expectedDeprecation)
        }

        then:
        succeeds()

        where:
        // Integer/Number are subtypes of Number, and Flavor/Named implement Named — all allowlisted.
        // Object, List, Number[] are not allowlisted and trigger the deprecation.
        // Note: for Number[] the deprecation message uses Class.getName() (JVM binary form) for the
        // type portion but the canonical name for the attribute-name portion.
        type       | value                              | expectedDeprecation
        "Integer"  | "123"                              | null
        "Number"   | "123"                              | null
        "Object"   | "123"                              | unsupportedTypeDeprecation("java.lang.Object", "java.lang.Object")
        "List"     | "['string']"                       | unsupportedTypeDeprecation("java.util.List", "java.util.List")
        "Flavor"   | "objects.named(Flavor, 'abc')"     | null
        "Named"    | "objects.named(Named, 'abc')"      | null
        "Number[]" | "[1, 1.2] as Number[]"             | unsupportedTypeDeprecation("[Ljava.lang.Number;", "java.lang.Number[]")
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
        // Attribute.of(List) trips the unsupported-type deprecation; the isolation logic itself
        // still works (List of Serializable Named is isolatable) so the build succeeds.
        executer.expectDocumentedDeprecationWarning(unsupportedTypeDeprecation("java.util.List", "java.util.List"))
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

    def "attribute container hash codes are stable across invocations (#description)"() {
        buildFile << """
            def color = Attribute.of("color", String)
            def shape = Attribute.of("shape", String)

            def attrs = configurations.create("foo").attributes
            ${attributeSetup}
            println("Hash: \${attrs.asImmutable().hashCode()}")
            println(System.getProperty("foo")) // To invalidate CC
        """

        when:
        succeeds("help", '--no-daemon', '-Dfoo=1')
        def hash1 = (output =~ /Hash: (-?\d+)/)[0][1]

        and:
        succeeds("help", '--no-daemon', "-Dfoo=2")
        def hash2 = (output =~ /Hash: (-?\d+)/)[0][1]

        then:
        hash1 == hash2

        where:
        description         | attributeSetup
        "no attributes"     | ""
        "single attribute"  | 'attrs.attribute(color, "green")'
        "two attributes"    | 'attrs.attribute(color, "green"); attrs.attribute(shape, "square")'
    }

}
