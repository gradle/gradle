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

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ParentProjectPropertyLookupIntegrationTest extends AbstractIntegrationSpec {

    private static final String DISABLE_PARENT_SCOPE = "-Dorg.gradle.internal.project.implicit-parent-properties=false"

    // region Status quo — parent lookup works by default

    def "parent extra property is accessible from child by default"() {
        settingsFile """
            include 'sub'
        """
        buildFile """
            ext.foo = "bar"
        """
        buildFile "sub/build.gradle", """
            println("result: " + foo)
        """

        when:
        succeeds("help")

        then:
        outputContains("result: bar")
    }

    // endregion

    // region Parent lookup disabled — Groovy DSL

    def "child cannot access parent extra property without implicit parent lookup"() {
        settingsFile """
            include 'sub'
        """
        buildFile """
            ext.foo = "bar"
        """
        buildFile "sub/build.gradle", """
            println(foo)
        """

        when:
        fails("help", DISABLE_PARENT_SCOPE)

        then:
        failure.assertHasCause("Could not get unknown property 'foo' for project ':sub' of type org.gradle.api.Project.")
    }

    def "findProperty returns null for parent extra property without implicit parent lookup"() {
        settingsFile """
            include 'sub'
        """
        buildFile """
            ext.foo = "bar"
        """
        buildFile "sub/build.gradle", """
            println("result: " + findProperty("foo"))
        """

        when:
        succeeds("help", DISABLE_PARENT_SCOPE)

        then:
        outputContains("result: null")
    }

    def "hasProperty returns false for parent extra property without implicit parent lookup"() {
        settingsFile """
            include 'sub'
        """
        buildFile """
            ext.foo = "bar"
        """
        buildFile "sub/build.gradle", """
            println("result: " + hasProperty("foo"))
        """

        when:
        succeeds("help", DISABLE_PARENT_SCOPE)

        then:
        outputContains("result: false")
    }

    def "property() throws for parent extra property without implicit parent lookup"() {
        settingsFile """
            include 'sub'
        """
        buildFile """
            ext.foo = "bar"
        """
        buildFile "sub/build.gradle", """
            property("foo")
        """

        when:
        fails("help", DISABLE_PARENT_SCOPE)

        then:
        failure.assertHasCause("Could not get unknown property 'foo' for project ':sub' of type org.gradle.api.Project.")
    }

    def "getProperty() throws for parent extra property without implicit parent lookup"() {
        settingsFile """
            include 'sub'
        """
        buildFile """
            ext.foo = "bar"
        """
        buildFile "sub/build.gradle", """
            getProperty("foo")
        """

        when:
        fails("help", DISABLE_PARENT_SCOPE)

        then:
        failure.assertHasCause("Could not get unknown property 'foo' for project ':sub' of type org.gradle.api.Project.")
    }

    def "properties map does not contain parent extra properties without implicit parent lookup"() {
        settingsFile """
            include 'sub'
        """
        buildFile """
            ext.foo = "bar"
        """
        buildFile "sub/build.gradle", """
            println("result: " + properties.containsKey("foo"))
        """

        when:
        succeeds("help", DISABLE_PARENT_SCOPE)

        then:
        outputContains("result: false")
    }

    def "child cannot access parent method without implicit parent lookup"() {
        settingsFile """
            include 'sub'
        """
        buildFile """
            ext.greet = { -> "hello" }
        """
        buildFile "sub/build.gradle", """
            greet()
        """

        when:
        fails("help", DISABLE_PARENT_SCOPE)

        then:
        failure.assertHasCause("Could not find method greet() for arguments [] on project ':sub' of type org.gradle.api.Project.")
    }

    def "child can still access own extra properties without implicit parent lookup"() {
        settingsFile """
            include 'sub'
        """
        buildFile "sub/build.gradle", """
            ext.myProp = "mine"
            println("result: " + myProp)
        """

        when:
        succeeds("help", DISABLE_PARENT_SCOPE)

        then:
        outputContains("result: mine")
    }

    def "child can still access own gradle.properties without implicit parent lookup"() {
        settingsFile """
            include 'sub'
        """
        file("sub/gradle.properties") << "myProp=value"
        buildFile "sub/build.gradle", """
            println("result: " + myProp)
        """

        when:
        succeeds("help", DISABLE_PARENT_SCOPE)

        then:
        outputContains("result: value")
    }

    def "build-scoped gradle.properties are visible in child regardless of implicit parent lookup (flag=#flagValue)"() {
        settingsFile """
            include 'sub'
        """
        file("gradle.properties") << "buildProp=fromBuildRoot"
        buildFile "sub/build.gradle", """
            println("result: " + buildProp)
        """

        when:
        succeeds("help", *args)

        then:
        outputContains("result: fromBuildRoot")

        where:
        flagValue | args
        true      | []
        false     | [DISABLE_PARENT_SCOPE]
    }

    def "child gradle.properties shadows build-scoped gradle.properties regardless of implicit parent lookup (flag=#flagValue)"() {
        settingsFile """
            include 'sub'
        """
        file("gradle.properties") << "shared=root"
        file("sub/gradle.properties") << "shared=child"
        buildFile "sub/build.gradle", """
            println("result: " + shared)
        """

        when:
        succeeds("help", *args)

        then:
        outputContains("result: child")

        where:
        flagValue | args
        true      | []
        false     | [DISABLE_PARENT_SCOPE]
    }

    def "dynamic container element creation works without implicit parent lookup"() {
        settingsFile """
            include 'sub'
        """
        buildFile "sub/build.gradle", """
            configurations { myConf { } }
            println("result: " + configurations.findByName("myConf")?.name)
        """

        when:
        succeeds("help", DISABLE_PARENT_SCOPE)

        then:
        outputContains("result: myConf")
    }

    def "dynamic container element creation no longer accidentally resolves from parent without implicit parent lookup"() {
        settingsFile """
            include 'sub'
        """
        buildFile """
            ext.foo = "parentFoo"
        """
        buildFile "sub/build.gradle", """
            configurations { foo { } }
            println("result: " + configurations.findByName("foo")?.name)
        """

        when:
        succeeds("help", DISABLE_PARENT_SCOPE)

        then:
        outputContains("result: foo")
    }

    def "accidental parent extension configuration is prevented without implicit parent lookup"() {
        settingsFile """
            include 'sub'
        """
        buildFile """
            interface Foo {
                Property<Integer> getValue()
            }
            extensions.create("foo", Foo)
            foo { value = 42 }
        """
        buildFile "sub/build.gradle", """
            foo { value = 99 }
        """

        when:
        fails("help", DISABLE_PARENT_SCOPE)

        then:
        failure.assertHasCause("Could not find method foo() for arguments")
    }

    def "transitive parent property lookup is disabled without implicit parent lookup"() {
        settingsFile """
            include 'sub', 'sub:sub-a'
        """
        buildFile """
            ext.transitive = "root"
        """
        buildFile "sub/sub-a/build.gradle", """
            println(transitive)
        """

        when:
        fails("help", DISABLE_PARENT_SCOPE)

        then:
        failure.assertHasCause("Could not get unknown property 'transitive' for project ':sub:sub-a' of type org.gradle.api.Project.")
    }

    // endregion

    // region Parent lookup disabled — Kotlin DSL (by project)

    def "nullable delegated property from parent returns null without implicit parent lookup"() {
        file("settings.gradle.kts") << """
            include("sub")
        """
        file("build.gradle.kts") << """
            extra["foo"] = "bar"
        """
        file("sub/build.gradle.kts") << """
            val foo: String? by project
            println("result: \$foo")
        """

        when:
        succeeds("help", DISABLE_PARENT_SCOPE)

        then:
        outputContains("result: null")
    }

    def "non-nullable delegated property from parent throws without implicit parent lookup"() {
        file("settings.gradle.kts") << """
            include("sub")
        """
        file("build.gradle.kts") << """
            extra["foo"] = "bar"
        """
        file("sub/build.gradle.kts") << """
            val foo: String by project
            println(foo)
        """

        when:
        fails("help", DISABLE_PARENT_SCOPE)

        then:
        failure.assertHasDescription("Cannot get non-null property 'foo' on project ':sub' as it does not exist")
    }

    def "nullable delegated property from parent resolves with implicit parent lookup (default)"() {
        file("settings.gradle.kts") << """
            include("sub")
        """
        file("build.gradle.kts") << """
            extra["foo"] = "bar"
        """
        file("sub/build.gradle.kts") << """
            val foo: String? by project
            println("result: \$foo")
        """

        when:
        succeeds("help")

        then:
        outputContains("result: bar")
    }

    def "non-nullable delegated property from parent resolves with implicit parent lookup (default)"() {
        file("settings.gradle.kts") << """
            include("sub")
        """
        file("build.gradle.kts") << """
            extra["foo"] = "bar"
        """
        file("sub/build.gradle.kts") << """
            val foo: String by project
            println("result: \$foo")
        """

        when:
        succeeds("help")

        then:
        outputContains("result: bar")
    }

    def "own extra properties still accessible via Kotlin delegation without implicit parent lookup"() {
        file("settings.gradle.kts") << """
            include("sub")
        """
        file("sub/build.gradle.kts") << """
            extra["myProp"] = "mine"
            val myProp: String by project
            println("result: \$myProp")
        """

        when:
        succeeds("help", DISABLE_PARENT_SCOPE)

        then:
        outputContains("result: mine")
    }

    def "own gradle.properties accessible via Kotlin delegation without implicit parent lookup"() {
        file("settings.gradle.kts") << """
            include("sub")
        """
        file("sub/gradle.properties") << "myProp=value"
        file("sub/build.gradle.kts") << """
            val myProp: String by project
            println("result: \$myProp")
        """

        when:
        succeeds("help", DISABLE_PARENT_SCOPE)

        then:
        outputContains("result: value")
    }

    // endregion
}
