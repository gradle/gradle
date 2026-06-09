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

/**
 * Verifies that a child project does not implicitly resolve properties or methods from its
 * parent projects. The implicit parent-hierarchy lookup was deprecated in Gradle 9.6 and
 * removed in Gradle 10: an unresolved reference now fails at the lookup site.
 */
class ParentProjectPropertyLookupIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        settingsFile << """
            rootProject.name = "root"
            include("a")
        """
    }

    def "child #access does not resolve a property declared only in the parent"() {
        given:
        buildFile << """
            ext.foo = "from-root"
        """
        file("a/build.gradle") << """
            println("result: " + $invocation)
        """

        expect:
        succeeds("help")
        outputContains("result: $expected")

        where:
        access           | invocation             | expected
        "findProperty()" | 'findProperty("foo")'  | "null"
        "hasProperty()"  | 'hasProperty("foo")'   | "false"
    }

    def "child #access fails for a property declared only in the parent"() {
        given:
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

    def "child #access fails for a method declared only in the parent"() {
        given:
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

    def "child can still access its own properties and methods"() {
        given:
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

    def "no failure when the property doesn't exist anywhere"() {
        given:
        file("a/build.gradle") << """
            println("missing: " + findProperty("missing"))
        """

        expect:
        succeeds("help")
        outputContains("missing: null")
    }

    def "the properties task on a subproject omits parent properties"() {
        given:
        buildFile << """
            ext.foo = "from-root"
        """
        file("a/build.gradle") << """
            ext.bar = "child-only"
        """

        expect:
        succeeds(":a:properties")
        outputDoesNotContain("foo: from-root")
        outputContains("bar: child-only")
    }
}
