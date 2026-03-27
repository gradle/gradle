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

package org.gradle.internal.cc.impl.isolated

class IsolatedProjectsParentProjectPropertyLookupIntegrationTest extends AbstractIsolatedProjectsIntegrationTest {

    private static final String DISABLE_PARENT_SCOPE = "-Dorg.gradle.internal.project.implicit-parent-properties=false"

    // region Groovy DSL — no IP violations when parent scope is disabled

    def "no IP violation when parent property lookup is disabled via dynamic resolution"() {
        settingsFile """
            include("sub")
        """
        buildFile """
            ext.foo = "bar"
        """
        buildFile "sub/build.gradle", """
            println(foo)
        """

        when:
        isolatedProjectsFails("help", DISABLE_PARENT_SCOPE)

        then:
        failure.assertHasCause("Could not get unknown property 'foo' for project ':sub' of type org.gradle.api.Project.")
    }

    def "no IP violation on findProperty without implicit parent lookup"() {
        settingsFile """
            include("sub")
        """
        buildFile """
            ext.foo = "bar"
        """
        buildFile "sub/build.gradle", """
            println("result: " + findProperty("foo"))
        """

        when:
        isolatedProjectsRun("help", DISABLE_PARENT_SCOPE)

        then:
        outputContains("result: null")
        fixture.assertStateStored {
            projectsConfigured(":", ":sub")
        }
    }

    def "no IP violation on hasProperty without implicit parent lookup"() {
        settingsFile """
            include("sub")
        """
        buildFile """
            ext.foo = "bar"
        """
        buildFile "sub/build.gradle", """
            println("result: " + hasProperty("foo"))
        """

        when:
        isolatedProjectsRun("help", DISABLE_PARENT_SCOPE)

        then:
        outputContains("result: false")
        fixture.assertStateStored {
            projectsConfigured(":", ":sub")
        }
    }

    def "no IP violation on properties map access without implicit parent lookup"() {
        settingsFile """
            include("sub")
        """
        buildFile """
            ext.foo = "bar"
        """
        buildFile "sub/build.gradle", """
            println("result: " + properties.containsKey("foo"))
        """

        when:
        isolatedProjectsRun("help", DISABLE_PARENT_SCOPE)

        then:
        outputContains("result: false")
        fixture.assertStateStored {
            projectsConfigured(":", ":sub")
        }
    }

    def "no IP violation on non-existing property access without implicit parent lookup"() {
        settingsFile """
            include("sub")
        """
        buildFile "sub/build.gradle", """
            getProperty("doesNotExist")
        """

        when:
        isolatedProjectsFails("help", DISABLE_PARENT_SCOPE)

        then:
        failure.assertHasCause("Could not get unknown property 'doesNotExist' for project ':sub' of type org.gradle.api.Project.")
    }

    def "no IP violation on non-existing method call without implicit parent lookup"() {
        settingsFile """
            include("sub")
        """
        buildFile "sub/build.gradle", """
            doesNotExist()
        """

        when:
        isolatedProjectsFails("help", DISABLE_PARENT_SCOPE)

        then:
        failure.assertHasCause("Could not find method doesNotExist() for arguments [] on project ':sub' of type org.gradle.api.Project.")
    }

    def "no IP violation on dynamic container element creation without implicit parent lookup"() {
        settingsFile """
            include("sub")
        """
        buildFile "sub/build.gradle", """
            configurations { myConf { } }
            println("result: " + configurations.findByName("myConf")?.name)
        """

        when:
        isolatedProjectsRun("help", DISABLE_PARENT_SCOPE)

        then:
        outputContains("result: myConf")
        fixture.assertStateStored {
            projectsConfigured(":", ":sub")
        }
    }

    def "no IP violation on properties access without implicit parent lookup"() {
        settingsFile """
            include("sub")
        """
        buildFile "sub/build.gradle", """
            println("result: " + properties.size())
        """

        when:
        isolatedProjectsRun("help", DISABLE_PARENT_SCOPE)

        then:
        outputContains("result: ")
        fixture.assertStateStored {
            projectsConfigured(":", ":sub")
        }
    }

    def "IP violations still reported for explicit cross-project access even without implicit parent lookup"() {
        settingsFile """
            include("sub")
        """
        buildFile """
            ext.foo = "bar"
        """
        buildFile "sub/build.gradle", """
            println(parent.ext.foo)
        """

        when:
        isolatedProjectsFails("help", DISABLE_PARENT_SCOPE)

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":sub")
            problem("Build file 'sub/build.gradle': line 2: Project ':sub' cannot access 'ext' extension on another project ':'")
        }
    }

    def "no IP violation for accidental parent extension configuration without implicit parent lookup"() {
        settingsFile """
            include("sub")
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
        isolatedProjectsFails("help", DISABLE_PARENT_SCOPE)

        then:
        failure.assertHasCause("Could not find method foo() for arguments")
    }

    def "deep nesting: no IP violation on transitive parent lookup without implicit parent lookup"() {
        settingsFile """
            include("sub")
            include("sub:sub-a")
        """
        buildFile """
            ext.transitive = "root"
        """
        buildFile "sub/sub-a/build.gradle", """
            println(transitive)
        """

        when:
        isolatedProjectsFails("help", DISABLE_PARENT_SCOPE)

        then:
        failure.assertHasCause("Could not get unknown property 'transitive' for project ':sub:sub-a' of type org.gradle.api.Project.")
    }

    // endregion

    // region Kotlin DSL — no IP violations when parent scope is disabled

    def "no IP violation on Kotlin nullable delegated parent property without implicit parent lookup"() {
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
        isolatedProjectsRun("help", DISABLE_PARENT_SCOPE)

        then:
        outputContains("result: null")
        fixture.assertStateStored {
            projectsConfigured(":", ":sub")
        }
    }

    def "no IP violation on Kotlin non-nullable delegated parent property without implicit parent lookup"() {
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
        isolatedProjectsFails("help", DISABLE_PARENT_SCOPE)

        then:
        failure.assertHasDescription("Cannot get non-null property 'foo' on project ':sub' as it does not exist")
    }

    // endregion
}
