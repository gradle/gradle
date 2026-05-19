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
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions

@Requires(value = TestExecutionPreconditions.NotIsolatedProjects, reason = "Under Isolated Projects, parent-project lookup is disabled entirely (see IsolatedProjectsAccessFromGroovyDslIntegrationTest); no deprecation fires")
class ParentProjectPropertyLookupIntegrationTest extends AbstractIntegrationSpec {

    private static final String PROPERTY_DEPRECATION = "Implicitly resolving properties in the project hierarchy has been deprecated. " +
        "This will fail with an error in Gradle 10. " +
        "Property 'foo' was not declared in project ':a' and was resolved from root project 'root'. " +
        "Consult the upgrading guide for further information: " +
        "https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_accessing_parent_project_properties"

    private static final String METHOD_DEPRECATION = "Implicitly resolving methods in the project hierarchy has been deprecated. " +
        "This will fail with an error in Gradle 10. " +
        "Method 'someMethod' was not declared in project ':a' and was resolved from root project 'root'. " +
        "Consult the upgrading guide for further information: " +
        "https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_accessing_parent_project_properties"

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
        executer.expectDocumentedDeprecationWarning(PROPERTY_DEPRECATION)

        then:
        succeeds("help")
        outputContains("foo: hello")
    }

    def "explicit findProperty(String) on the child project is deprecated when resolved from parent"() {
        given:
        buildFile << """
            ext.foo = "hello"
        """
        file("a/build.gradle") << """
            println("foo: " + findProperty("foo"))
        """

        when:
        executer.expectDocumentedDeprecationWarning(PROPERTY_DEPRECATION)

        then:
        succeeds("help")
        outputContains("foo: hello")
    }

    def "explicit property(String) on the child project is deprecated when resolved from parent"() {
        given:
        buildFile << """
            ext.foo = "hello"
        """
        file("a/build.gradle") << """
            println("foo: " + property("foo"))
        """

        when:
        executer.expectDocumentedDeprecationWarning(PROPERTY_DEPRECATION)

        then:
        succeeds("help")
        outputContains("foo: hello")
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
        executer.expectDocumentedDeprecationWarning(METHOD_DEPRECATION)

        then:
        succeeds("help")
        outputContains("result: hello")
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
}
