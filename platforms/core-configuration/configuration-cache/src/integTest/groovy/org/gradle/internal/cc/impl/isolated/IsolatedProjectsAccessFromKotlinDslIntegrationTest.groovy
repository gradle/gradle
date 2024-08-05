/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.util.internal.ToBeImplemented
import spock.lang.Issue

class IsolatedProjectsAccessFromKotlinDslIntegrationTest extends AbstractIsolatedProjectsIntegrationTest {
    def "reports problem when build script uses #block block to apply plugins to another project"() {
        createDirs("a", "b")
        settingsFile << """
            include("a")
            include("b")
        """
        buildKotlinFile << """
            $block {
                plugins.apply("java-library")
            }
        """

        when:
        isolatedProjectsFails("assemble")

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":a", ":b")
            problem("Build file 'build.gradle.kts': Project ':' cannot access 'Project.plugins' functionality on $message", 2)
        }

        where:
        block         | message
        "allprojects" | "subprojects via 'allprojects'"
        "subprojects" | "subprojects"
    }

    def "reports problem when build script uses #block block to access dynamically added elements"() {
        createDirs("a", "b")
        settingsFile << """
            include("a")
            include("b")
        """
        buildKotlinFile << """
            plugins { id("java-library") }
            $block {
                plugins.apply("java-library")
                java { }
                java.sourceCompatibility
            }
        """

        when:
        isolatedProjectsFails("assemble")

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":a", ":b")
            problem("Build file 'build.gradle.kts': Project ':' cannot access 'Project.extensions' functionality on $message", 3)
            problem("Build file 'build.gradle.kts': Project ':' cannot access 'Project.plugins' functionality on $message", 3)
        }

        where:
        block         | message
        "allprojects" | "subprojects via 'allprojects'"
        "subprojects" | "subprojects"
    }

    @ToBeImplemented("when Isolated Projects becomes incremental for task execution")
    def "reports cross-project model access in project-level Gradle.#invocation"() {
        createDirs("a", "b")
        settingsFile << """
            include("a")
            include("b")
        """
        file("a/build.gradle.kts") << """
            gradle.${invocation} { println(buildDir) }
        """

        when:
        // TODO:isolated expected behavior for incremental configuration
//        isolatedProjectsFails(":a:help")
        isolatedProjectsRun(":a:help")

        then:
        // TODO:isolated expected behavior for incremental configuration
//        fixture.assertStateStoredAndDiscarded {
//            projectsConfigured(":", ":a")
//            problem("Build file 'a/build.gradle.kts': Project ':a' cannot access 'Project.buildDir' functionality on another project ':b'")
//        }
        fixture.assertStateStored {
            projectsConfigured(":", ":a", ":b")
        }

        where:
        invocation      | accessedProjects
        "beforeProject" | [":b"]
        "afterProject"  | [":b"]
    }

    def "reports cross-project model access from a listener added to Gradle.projectsEvaluated"() {
        createDirs("a", "b")
        settingsFile << """
            include("a")
            include("b")
        """
        file("a/build.gradle.kts") << """
            gradle.projectsEvaluated {
                allprojects { println(buildDir) }
            }
        """

        when:
        isolatedProjectsFails(":a:help", ":b:help")

        then:
        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":a", ":b")
            problem("Build file 'a/build.gradle.kts': Project ':a' cannot access 'Project.buildDir' functionality on subprojects of project ':'", 2)
        }
    }

    def "build script can query basic details of projects in a function called from allprojects block"() {
        createDirs("a", "b")
        settingsFile << """
            rootProject.name = "root"
            include("a", "b")
        """
        buildKotlinFile << """
            fun printInfo(p: Project) {
                println("project name = " + p.name)
            }

            allprojects {
                printInfo(project)
            }

            tasks.register("something")
        """

        when:
        isolatedProjectsRun("something")

        then:
        outputContains("project name = root")
        outputContains("project name = a")
        outputContains("project name = b")

        fixture.assertStateStored {
            projectsConfigured(":", ":a", ":b")
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/28204")
    def "access to #description delegated property value is causing a violation"() {
        given:
        settingsFile << """
            include("a")
        """
        buildKotlinFile << """
            project.extensions.extraProperties["myProperty"] = "hello"
        """

        // Requires a sub-project
        file("a/build.gradle.kts") << """
            val myProperty: $type by project
            println("myProperty: " + myProperty) // actual access to the value is required to trigger a lookup
        """

        when:
        isolatedProjectsFails("help")

        then:
        outputContains("myProperty: hello")

        fixture.assertStateStoredAndDiscarded {
            projectsConfigured(":", ":a")
            problem("Build file 'a/build.gradle.kts': Project ':a' cannot dynamically look up a property in the parent project ':'")
        }

        where:
        description    | type
        "nullable"     | "String?"
        "non-nullable" | "String"
    }
}
