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


import spock.lang.Issue

/**
 * Tests cross-project access for DSL-agnostic functionality.
 * <p>
 * For DSL-specific tests prefer:
 * <ul>
 *     <li>{@link IsolatedProjectsAccessFromGroovyDslIntegrationTest}</li>
 *     <li>{@link IsolatedProjectsAccessFromKotlinDslIntegrationTest}</li>
 * </ul>
 */
class IsolatedProjectsAccessIntegrationTest extends AbstractIsolatedProjectsIntegrationTest {

    @Issue("https://github.com/gradle/gradle/issues/37643")
    def "reports problem when accessing mutable state on a project in hierarchy"() {
        settingsFile """
            include(":sub:par")
        """

        buildFile "sub/par/build.gradle", """
            ${parentExpr}.findProperty("prop")
        """

        when:
        isolatedProjectsFailsUsing(mode, "help")

        then:
        fixture.assertIsolatedProjectsProblems(mode) {
            projectsConfigured(":", ":sub", ":sub:par")
            problem("Build file 'sub/par/build.gradle': line 2: Project ':sub:par' cannot access 'Project.findProperty' functionality on another project '$parentPath'", 1)
        }

        where:
        parentExpr      | parentPath
        "rootProject"   | ":"
        "parent"        | ":sub"
        "parent.parent" | ":"

        combined:
        mode << ALL_MODES
    }

    def "reports problem when using #api on a dependency on another project declared via #declaration"() {
        createDirs("a", "b")
        settingsFile """
            enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
            include(":a", ":b")
        """

        buildFile "a/build.gradle", """
            def dep = $declaration
            $expr
        """

        when:
        isolatedProjectsFailsUsing(mode, "help")

        then:
        fixture.assertIsolatedProjectsProblems(mode) {
            projectsConfigured(":", ":a", ":b")
            problem("Build file 'a/build.gradle': line 3: Project ':a' cannot access '$api' of a dependency on another project ':b'$explanation", 1)
        }

        where:
        expr                                                                | api                                           | explanation
        "dep.group"                                                         | "ProjectDependency.getGroup()"                | ", as the value comes from that project's 'group' property"
        "dep.version"                                                       | "ProjectDependency.getVersion()"              | ", as the value comes from that project's 'version' property"
        "dep.capabilities { it.requireFeature('f') }.requestedCapabilities" | "ModuleDependency.getRequestedCapabilities()" | " that requires a feature, as the capability comes from that project's 'group' and 'version' properties. Use 'ModuleDependency.getCapabilitySelectors()' instead"

        combined:
        declaration << ['dependencies.project(path: ":b")', "projects.b"]

        combined:
        mode << ALL_MODES
    }

    def "can use #expr on a dependency on its own project declared via #declaration"() {
        createDirs("a")
        settingsFile """
            enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
            include(":a")
        """

        buildFile "a/build.gradle", """
            def dep = $declaration
            println("value: " + $expr)
        """

        when:
        isolatedProjectsRun("help")

        then:
        fixture.assertStateStored {
            projectsConfigured(":", ":a")
        }

        where:
        expr << [
            "dep.group",
            "dep.version",
            "dep.capabilities { it.requireFeature('f') }.requestedCapabilities",
        ]

        combined:
        declaration << ['dependencies.project(path: ":a")', "projects.a"]
    }

    @Issue("https://github.com/gradle/gradle/issues/29154")
    def "exclude task option does not cause isolated projects violation"() {
        settingsFile """
            include(":sub")
        """

        buildFile """
            task build
        """

        buildFile "sub/build.gradle", """
            task test
            task build { dependsOn test }
        """

        when:
        isolatedProjectsRun(":sub:build", "-x", "test")

        then:
        fixture.assertStateStored {
            projectsConfigured(":", ":sub")
        }
        result.assertTasksNotScheduled(":sub:test")
    }
}
