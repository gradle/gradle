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
import org.gradle.test.fixtures.file.TestFile

class ParentProjectPropertyLookupIntegrationTest extends AbstractIntegrationSpec {

    public static final String GET_PROPERTIES_DEPRECATION_POSTFIX = "This will fail with an error in Gradle 10. " +
        "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_get_properties"
    public static final String DYNAMICALLY_INVOKING_PARENT_DEPRECATION = "Dynamically invoking parent method from a child project has been deprecated. This will fail with an error in Gradle 10. Cannot dynamically invoke method 'foo' on project ':parent' from project ':parent:child'."
    public static final String HAS_PROPERTY_DEPRECATION = "Calling 'hasProperty' to query presence of property from parent project has been deprecated. This will fail with an error in Gradle 10. Tried to query parent project project ':parent' for presence property 'value' from project ':parent:child'."
    public static final String GET_PROPERTIES_DEPRECATION = "Dynamically calling getProperties() on a script has been deprecated. " + GET_PROPERTIES_DEPRECATION_POSTFIX

    private static String implicitPropertyDeprecation(String propertyName) {
        "Accessing a property from a parent project has been deprecated. This will fail with an error in Gradle 10. Property '$propertyName' was not found in project ':parent:child' and was dynamically resolved from project ':parent'."
    }

    private static String explicitPropertyDeprecation(String callerApi, String propertyName) {
        "Calling '$callerApi' to retrieve property from parent project has been deprecated. This will fail with an error in Gradle 10. Tried to query parent project project ':parent' for property '$propertyName' from project ':parent:child'."
    }

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

    def "implicitly getting property from parent is deprecated"() {
        parent << """
            ext.value = "foo"
        """
        child << """
            println(value)
        """

        when:
        executer.expectDocumentedDeprecationWarning(implicitPropertyDeprecation("value"))
        succeeds("help")

        then:
        outputContains("foo")
    }

    def "implicitly getting property from grandparent is deprecated"() {
        grandparent << """
            ext.value = "foo"
        """
        child << """
            println(value)
        """

        when:
        executer.expectDocumentedDeprecationWarning(implicitPropertyDeprecation("value"))
        succeeds("help")

        then:
        outputContains("foo")
    }

    def "implicitly invoking closure property on parent is deprecated"() {
        parent << """
            ext.foo = { println('called') }
        """
        child << """
            foo()
        """

        when:
        executer.expectDocumentedDeprecationWarning(implicitPropertyDeprecation("foo"))
        succeeds("help")

        then:
        outputContains("called")
    }

    def "implicitly invoking method on parent is deprecated"() {
        parent << """
            def foo() { println('called') }
        """
        child << """
            foo()
        """

        when:
        executer.expectDocumentedDeprecationWarning(DYNAMICALLY_INVOKING_PARENT_DEPRECATION)
        succeeds("help")

        then:
        outputContains("called")
    }

    def "implicitly invoking closure property on grandparent is deprecated"() {
        grandparent << """
            ext.foo = { println('called') }
        """
        child << """
            foo()
        """

        when:
        executer.expectDocumentedDeprecationWarning(implicitPropertyDeprecation("foo"))
        succeeds("help")

        then:
        outputContains("called")
    }

    def "implicitly invoking method on grandparent is deprecated"() {
        grandparent << """
            def foo() { println('called') }
        """
        child << """
            foo()
        """

        when:
        executer.expectDocumentedDeprecationWarning(DYNAMICALLY_INVOKING_PARENT_DEPRECATION)
        succeeds("help")

        then:
        outputContains("called")
    }

    def "getting property through dynamic getProperty from parent is deprecated"() {
        parent << """
            ext.value = "foo"
        """
        child << """
            println(getProperty("value"))
        """

        when:
        // In a Groovy script, getProperty("value") is resolved on the script object,
        // which delegates to the project's dynamic object via the same path as bare 'value'.
        executer.expectDocumentedDeprecationWarning(implicitPropertyDeprecation("value"))
        succeeds("help")

        then:
        outputContains("foo")
    }

    def "getting property through dynamic getProperty from grandparent is deprecated"() {
        grandparent << """
            ext.value = "foo"
        """
        child << """
            println(getProperty("value"))
        """

        when:
        executer.expectDocumentedDeprecationWarning(implicitPropertyDeprecation("value"))
        succeeds("help")

        then:
        outputContains("foo")
    }

    def "querying presence of parent property through dynamic hasProperty is deprecated"() {
        parent << """
            ext.value = "foo"
        """
        child << """
            assert hasProperty("value")
        """

        when:
        executer.expectDocumentedDeprecationWarning(HAS_PROPERTY_DEPRECATION)
        succeeds("help")

        then:
        noExceptionThrown()
    }

    def "querying presence of grandparent property through dynamic hasProperty is deprecated"() {
        grandparent << """
            ext.value = "foo"
        """
        child << """
            assert hasProperty("value")
        """

        when:
        executer.expectDocumentedDeprecationWarning(HAS_PROPERTY_DEPRECATION)
        succeeds("help")

        then:
        noExceptionThrown()
    }

    def "getting property through static getProperty from parent is deprecated"() {
        parent << """
            ext.value = "foo"
        """
        childKts << """
            println(property("value"))
        """

        when:
        executer.expectDocumentedDeprecationWarning(explicitPropertyDeprecation("property()", "value"))
        succeeds("help")

        then:
        outputContains("foo")
    }

    def "getting property through static getProperty from grandparent is deprecated"() {
        grandparent << """
            ext.value = "foo"
        """
        childKts << """
            println(property("value"))
        """

        when:
        executer.expectDocumentedDeprecationWarning(explicitPropertyDeprecation("property()", "value"))
        succeeds("help")

        then:
        outputContains("foo")
    }

    def "querying presence of parent property through static hasProperty is deprecated"() {
        parent << """
            ext.value = "foo"
        """
        childKts << """
            require(project.hasProperty("value"))
        """

        when:
        executer.expectDocumentedDeprecationWarning(HAS_PROPERTY_DEPRECATION)
        succeeds("help")

        then:
        noExceptionThrown()
    }

    def "querying presence of grandparent property through static hasProperty is deprecated"() {
        grandparent << """
            ext.value = "foo"
        """
        childKts << """
            require(project.hasProperty("value"))
        """

        when:
        executer.expectDocumentedDeprecationWarning(HAS_PROPERTY_DEPRECATION)
        succeeds("help")

        then:
        noExceptionThrown()
    }

    def "statically getting properties of a project is deprecated"() {
        buildKotlinFile << """
            properties
        """

        expect:
        executer.expectDocumentedDeprecationWarning("The Project.getProperties method has been deprecated. " + GET_PROPERTIES_DEPRECATION_POSTFIX)
        succeeds("help")
    }

    def "dynamically getting properties of a project is deprecated"() {
        buildFile << """
            properties
        """

        expect:
        executer.expectDocumentedDeprecationWarning(GET_PROPERTIES_DEPRECATION)
        succeeds("help")
    }

    def "dynamically getting properties of a settings script is deprecated"() {
        settingsFile << """
            properties
        """

        expect:
        executer.expectDocumentedDeprecationWarning(GET_PROPERTIES_DEPRECATION)
        succeeds("help")
    }

    def "dynamically getting properties of init script is deprecated"() {
        file("init.gradle") << """
            properties
        """

        when:
        executer.expectDocumentedDeprecationWarning(GET_PROPERTIES_DEPRECATION)
        fails("help", "-I", "init.gradle")

        then:
        failure.assertHasCause("The default project is not yet available for build")
    }
}
