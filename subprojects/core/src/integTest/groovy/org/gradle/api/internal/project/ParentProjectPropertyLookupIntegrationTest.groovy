/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.project

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ParentProjectAccessDeprecations
import org.gradle.test.fixtures.file.TestFile

class ParentProjectPropertyLookupIntegrationTest extends AbstractIntegrationSpec implements ParentProjectAccessDeprecations {

    TestFile grandparent = buildFile
    TestFile parent = file("parent/build.gradle")
    TestFile child = file("parent/child/build.gradle")
    TestFile childKts = file("parent/child/build.gradle.kts")

    def setup() {
        settingsFile << """
            rootProject.name = "grandparent"
            include(":parent")
            include(":parent:child")
        """
        createDirs("parent", "parent/child")
        parent.touch()
    }

    def "property access '#expr' emits deprecation when property is only on parent"() {
        parent << """
            ext.value = "parentValue"
        """
        child << """
            $accessCode
        """

        when:
        if (deprecationType == "implicit") {
            expectImplicitParentPropertyDeprecation("value", "project ':parent'", "project ':parent:child'")
        } else if (deprecationType == "explicit") {
            expectExplicitParentPropertyDeprecation(callerApi, "value", "project ':parent:child'", "project ':parent'")
        } else if (deprecationType == "hasProperty") {
            expectParentHasPropertyDeprecation("value", "project ':parent:child'", "project ':parent'")
        }
        succeeds("help")

        then:
        outputContains("parentValue")

        where:
        expr                     | accessCode                                             | deprecationType | callerApi
        "value"                  | 'println(value)'                                       | "implicit"      | null
        "getProperty('value')"   | 'println(getProperty("value"))'                        | "implicit"      | null
        "property('value')"      | 'println(property("value"))'                           | "explicit"      | "property()"
        "findProperty('value')"  | 'println(findProperty("value"))'                       | "explicit"      | "findProperty()"
        "hasProperty('value')"   | 'if(hasProperty("value")) println("parentValue")'      | "hasProperty"   | null
    }

    def "property access '#expr' emits no deprecation when property is on child (#scenario)"() {
        if (alsoOnParent) {
            parent << """
                ext.value = "parentValue"
            """
        }
        child << """
            ext.value = "childValue"
            $accessCode
        """

        when:
        succeeds("help")

        then:
        outputContains("childValue")

        where:
        expr                     | accessCode                                    | alsoOnParent
        "value"                  | 'println(value)'                              | false
        "value"                  | 'println(value)'                              | true
        "getProperty('value')"   | 'println(getProperty("value"))'               | false
        "getProperty('value')"   | 'println(getProperty("value"))'               | true
        "property('value')"      | 'println(property("value"))'                  | false
        "property('value')"      | 'println(property("value"))'                  | true
        "findProperty('value')"  | 'println(findProperty("value"))'              | false
        "findProperty('value')"  | 'println(findProperty("value"))'              | true
        "hasProperty('value')"   | 'if(hasProperty("value")) println(value)'     | false
        "hasProperty('value')"   | 'if(hasProperty("value")) println(value)'     | true

        scenario = alsoOnParent ? "also on parent" : "only on child"
    }

    def "#expr returns #expectedOutput when property is not defined anywhere"() {
        child << """
            $accessCode
        """

        when:
        succeeds("help")

        then:
        outputContains(expectedOutput)

        where:
        expr                     | accessCode                                   | expectedOutput
        "findProperty('value')"  | 'println("found: " + findProperty("value"))' | "found: null"
        "hasProperty('value')"   | 'println("has: " + hasProperty("value"))'    | "has: false"
    }

    def "method invocation on parent emits deprecation"() {
        parent << """
            def foo() { println('parent') }
        """
        child << """
            foo()
        """

        when:
        expectParentMethodDeprecation("foo", "project ':parent'", "project ':parent:child'")
        succeeds("help")

        then:
        outputContains("parent")
    }

    def "method invocation emits no deprecation when method is on child"() {
        parent << """
            def foo() { println('parent') }
        """
        child << """
            def foo() { println('child') }
            foo()
        """

        when:
        succeeds("help")

        then:
        outputContains("child")
    }

    def "closure property invocation on parent emits deprecation"() {
        parent << """
            ext.foo = { println('parent') }
        """
        child << """
            foo()
        """

        when:
        expectImplicitParentPropertyDeprecation("foo", "project ':parent'", "project ':parent:child'")
        succeeds("help")

        then:
        outputContains("parent")
    }

    def "grandparent property resolution emits deprecation"() {
        grandparent << """
            ext.value = "grandparentValue"
        """
        child << """
            println(value)
        """

        when:
        expectImplicitParentPropertyDeprecation("value", "project ':parent'", "project ':parent:child'")
        succeeds("help")

        then:
        outputContains("grandparentValue")
    }

    def "grandparent method resolution emits deprecation"() {
        grandparent << """
            def foo() { println('parent') }
        """
        child << """
            foo()
        """

        when:
        expectParentMethodDeprecation("foo", "project ':parent'", "project ':parent:child'")
        succeeds("help")

        then:
        outputContains("parent")
    }

    // Container element creation — Groovy checks the parent for the element name as a property
    // before creating a new element in the container. This should not trigger a deprecation.

    def "creating a new configuration does not trigger parent property deprecation"() {
        child << """
            configurations {
                myNewConfiguration {
                }
            }
            println("created: " + configurations.myNewConfiguration.name)
        """

        when:
        succeeds("help")

        then:
        outputContains("created: myNewConfiguration")
    }

    def "creating a new configuration does not trigger parent property deprecation even when parent has property with same name"() {
        parent << """
            ext.myNewConfiguration = "parentValue"
        """
        child << """
            configurations {
                myNewConfiguration {
                }
            }
            println("created: " + configurations.myNewConfiguration.name)
        """

        when:
        succeeds("help")

        then:
        outputContains("created: myNewConfiguration")
    }

    // NO_IMPLICIT_PARENT_PROPERTY_LOOKUP feature preview — opt-in to Gradle 10 behavior

    def "NO_IMPLICIT_PARENT_PROPERTY_LOOKUP feature preview disables parent property walking"() {
        settingsFile.text = """
            enableFeaturePreview("NO_IMPLICIT_PARENT_PROPERTY_LOOKUP")
        """ + settingsFile.text
        parent << """
            ext.value = "parentValue"
        """
        child << """
            println("found: " + findProperty("value"))
            println("hasIt: " + hasProperty("value"))
        """

        when:
        // No deprecation should fire — parent is not walked
        succeeds("help")

        then:
        outputContains("found: null")
        outputContains("hasIt: false")
    }

    def "NO_IMPLICIT_PARENT_PROPERTY_LOOKUP feature preview disables parent method walking"() {
        settingsFile.text = """
            enableFeaturePreview("NO_IMPLICIT_PARENT_PROPERTY_LOOKUP")
        """ + settingsFile.text
        parent << """
            def foo() { println('parent foo') }
        """
        child << """
            try {
                foo()
                println("NO EXCEPTION")
            } catch (groovy.lang.MissingMethodException e) {
                println("got MissingMethodException")
            }
        """

        when:
        succeeds("help")

        then:
        outputContains("got MissingMethodException")
    }

    // Kotlin DSL tests — property() and hasProperty() go through DefaultProject directly

    def "getting property through static property() from parent is deprecated (Kotlin DSL)"() {
        parent << """
            ext.value = "foo"
        """
        childKts << """
            println(property("value"))
        """

        when:
        expectExplicitParentPropertyDeprecation("property()", "value", "project ':parent:child'", "project ':parent'")
        succeeds("help")

        then:
        outputContains("foo")
    }

    def "querying presence of parent property through static hasProperty is deprecated (Kotlin DSL)"() {
        parent << """
            ext.value = "foo"
        """
        childKts << """
            require(project.hasProperty("value"))
        """

        when:
        expectParentHasPropertyDeprecation("value", "project ':parent:child'", "project ':parent'")
        succeeds("help")

        then:
        noExceptionThrown()
    }
}
