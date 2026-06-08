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
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions

@Requires(value = TestExecutionPreconditions.NotIsolatedProjects, reason = "Under Isolated Projects, parent-project lookup is disabled entirely (see IsolatedProjectsAccessFromGroovyDslIntegrationTest); no deprecation fires")
class ParentProjectPropertyLookupIntegrationTest extends AbstractIntegrationSpec {

    private static final String COMMON_DEPRECATION_PART = "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_implicit_lookup_in_parent_projects"

    private static String implicitPropertyDeprecation(String name) {
        propertyDeprecation(name, "project ':a'", "root project 'root'")
    }

    private static String implicitMethodDeprecation(String name) {
        methodDeprecation(name, "project ':a'", "root project 'root'")
    }

    private static String explicitApiDeprecation(String apiName, String name) {
        "Implicit lookup of properties in parent projects has been deprecated. " +
            "This will fail with an error in Gradle 10. " +
            "Property '$name' was not declared in project ':a' and was resolved from root project 'root'. " +
            "This lookup was initiated by '$apiName'. " +
            "$COMMON_DEPRECATION_PART"
    }

    private static String propertyDeprecation(String name, String lookupProject, String declaringProject) {
        "Implicit lookup of properties in parent projects has been deprecated. " +
            "This will fail with an error in Gradle 10. " +
            "Property '$name' was not declared in $lookupProject and was resolved from $declaringProject. " +
            "$COMMON_DEPRECATION_PART"
    }

    private static String methodDeprecation(String name, String lookupProject, String declaringProject) {
        "Implicit lookup of methods in parent projects has been deprecated. " +
            "This will fail with an error in Gradle 10. " +
            "Method '$name' was not declared in $lookupProject and was resolved from $declaringProject. " +
            "$COMMON_DEPRECATION_PART"
    }

    def setup() {
        settingsFile << """
            rootProject.name = "root"
            include("a")
        """
    }

    def "implicit property lookup from a child project is deprecated"() {
        given:
        buildFile << """
            ext.foo = "hello"
        """
        file("a/build.gradle") << """
            println("foo: " + foo)
        """

        when:
        executer.expectDocumentedDeprecationWarning(implicitPropertyDeprecation("foo"))

        then:
        succeeds("help")
        outputContains("foo: hello")
    }

    def "explicit #api on the child project is deprecated when resolved from parent"() {
        given:
        buildFile << """
            ext.foo = "hello"
        """
        file("a/build.gradle") << """
            println("result: " + $invocation)
        """

        when:
        executer.expectDocumentedDeprecationWarning(deprecation)

        then:
        succeeds("help")
        outputContains("result: $expected")

        where:
        api              | invocation             | expected | deprecation
        "findProperty()" | 'findProperty("foo")'  | "hello"  | explicitApiDeprecation("findProperty()", "foo")
        "property()"     | 'property("foo")'      | "hello"  | explicitApiDeprecation("property()", "foo")
        "getProperty()"  | 'getProperty("foo")'   | "hello"  | implicitPropertyDeprecation("foo")
        "hasProperty()"  | 'hasProperty("foo")'   | "true"   | implicitPropertyDeprecation("foo")
    }

    def "implicit method invocation from a child project is deprecated"() {
        given:
        buildFile << """
            def someMethod() { "hello" }
        """
        file("a/build.gradle") << """
            println("result: " + someMethod())
        """

        when:
        executer.expectDocumentedDeprecationWarning(implicitMethodDeprecation("someMethod"))

        then:
        succeeds("help")
        outputContains("result: hello")
    }

    def "deprecation names the ancestor project that actually declares the property"() {
        given:
        settingsFile << """
            include("a:b")
        """
        buildFile << """
            ext.foo = "from-root"
        """
        file("a/build.gradle") << ""
        file("a/b/build.gradle") << """
            println("foo: " + foo)
        """

        when:
        executer.expectDocumentedDeprecationWarning(propertyDeprecation("foo", "project ':a:b'", "root project 'root'"))

        then:
        succeeds("help")
        outputContains("foo: from-root")
    }

    def "deprecation names the closest ancestor when several ancestors declare the property"() {
        given:
        settingsFile << """
            include("a:b")
        """
        buildFile << """
            ext.foo = "from-root"
        """
        file("a/build.gradle") << """
            ext.foo = "from-middle"
        """
        file("a/b/build.gradle") << """
            println("foo: " + foo)
        """

        when:
        executer.expectDocumentedDeprecationWarning(propertyDeprecation("foo", "project ':a:b'", "project ':a'"))

        then:
        succeeds("help")
        outputContains("foo: from-middle")
    }

    def "deprecation names the ancestor project that actually declares the method"() {
        given:
        settingsFile << """
            include("a:b")
        """
        buildFile << """
            def someMethod() { "from-root" }
        """
        file("a/build.gradle") << ""
        file("a/b/build.gradle") << """
            println("result: " + someMethod())
        """

        when:
        executer.expectDocumentedDeprecationWarning(methodDeprecation("someMethod", "project ':a:b'", "root project 'root'"))

        then:
        succeeds("help")
        outputContains("result: from-root")
    }

    def "no deprecation when the property is defined locally in the child project"() {
        given:
        buildFile << """
            ext.foo = "from-root"
        """
        file("a/build.gradle") << """
            ext.foo = "from-child"
            println("foo: " + foo)
        """

        expect:
        succeeds("help")
        outputContains("foo: from-child")
    }

    def "no deprecation when the property doesn't exist anywhere"() {
        given:
        file("a/build.gradle") << """
            println("missing: " + findProperty("missing"))
        """

        expect:
        succeeds("help")
        outputContains("missing: null")
    }

    def "no deprecation when the property is defined only in the child project"() {
        given:
        file("a/build.gradle") << """
            ext.foo = "child-only"
            println("foo: " + foo)
        """

        expect:
        succeeds("help")
        outputContains("foo: child-only")
    }

    def "can disable implicit lookup in parent projects: child #access returns #expected for parent-only property"() {
        given:
        disableImplicitLookupInParentProjects()
        buildFile << """
            ext.foo = "from-root"
        """
        file("a/build.gradle") << """
            println("result: " + $invocation)
        """

        expect:
        // No deprecation expected — the feature preview disables the parent walk at the source.
        succeeds("help")
        outputContains("result: $expected")

        where:
        access            | invocation              | expected
        "findProperty()"  | 'findProperty("foo")'   | "null"
        "hasProperty()"   | 'hasProperty("foo")'    | "false"
    }

    def "can disable implicit lookup in parent projects: child #access throws MissingPropertyException for parent-only property"() {
        given:
        disableImplicitLookupInParentProjects()
        buildFile << """
            ext.foo = "from-root"
        """
        file("a/build.gradle") << """
            $invocation
        """

        expect:
        fails("help")
        failure.assertHasCause("Could not get unknown property 'foo' for project ':a' of type org.gradle.api.Project.")

        where:
        access            | invocation
        "implicit foo"    | 'println("foo: " + foo)'
        "property()"      | 'property("foo")'
        "getProperty()"   | 'getProperty("foo")'
    }

    def "can disable implicit lookup in parent projects: child #access throws MissingMethodException for parent-only method"() {
        given:
        disableImplicitLookupInParentProjects()
        buildFile << """
            def someMethod() { "from-root" }
        """
        file("a/build.gradle") << """
            $invocation
        """

        expect:
        fails("help")
        failure.assertHasCause("Could not find method someMethod() for arguments [] on project ':a' of type org.gradle.api.Project.")

        where:
        access                 | invocation
        "implicit someMethod"  | 'someMethod()'
        "this.someMethod"      | 'this.someMethod()'
        "project.someMethod"   | 'project.someMethod()'
    }

    def "can disable implicit lookup in parent projects: child can still access its own properties and methods"() {
        given:
        disableImplicitLookupInParentProjects()
        file("a/build.gradle") << """
            ext.local = "child-only"
            def localMethod() { "child-method" }
            println("prop: " + local)
            println("method: " + localMethod())
        """

        expect:
        succeeds("help")
        outputContains("prop: child-only")
        outputContains("method: child-method")
    }

    def "the properties task on a subproject reports parent properties without deprecation"() {
        given:
        buildFile << """
            ext.foo = "hello"
        """
        file("a/build.gradle") << ""

        expect:
        succeeds(":a:properties")
        outputContains("foo: hello")
    }

    def "the properties task on a subproject omits parent properties when implicit lookup is disabled"() {
        given:
        disableImplicitLookupInParentProjects()
        buildFile << """
            ext.foo = "hello"
        """
        file("a/build.gradle") << """
            ext.bar = "child-only"
        """

        expect:
        succeeds(":a:properties")
        outputDoesNotContain("foo: hello")
        outputContains("bar: child-only")
    }

    private TestFile disableImplicitLookupInParentProjects() {
        settingsFile << """
            enableFeaturePreview("NO_IMPLICIT_LOOKUP_IN_PARENT_PROJECTS")
        """
    }
}
