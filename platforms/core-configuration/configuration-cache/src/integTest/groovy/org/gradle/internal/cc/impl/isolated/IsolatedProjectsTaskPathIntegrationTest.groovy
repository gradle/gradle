/*
 * Copyright 2024 the original author or authors.
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

class IsolatedProjectsTaskPathIntegrationTest extends AbstractIsolatedProjectsIntegrationTest {

    def "#type task path dependency is not an IP violation"() {
        given:
        settingsFile << """
            include(":a")
        """
        buildFile << """
            tasks.register("foo") {
                dependsOn("$path")
            }
        """
        file("a/build.gradle") << """
            tasks.register("bar")
        """

        when:
        isolatedProjectsRun(":foo")

        then:
        fixture.assertStateStored {
            projectsConfigured(":", ":a")
        }

        where:
        type       | path
        "absolute" | ':a:bar'
        "relative" | 'a:bar'
    }

    def "root project task path dependency is not an IP violation"() {
        given:
        settingsFile << """
            include(":a")
        """
        buildFile << """
            tasks.register("bar")
        """
        file("a/build.gradle") << """
            tasks.register("foo") {
                dependsOn(":bar")
            }
        """

        when:
        isolatedProjectsRun(":a:foo")

        then:
        fixture.assertStateStored {
            projectsConfigured(":", ":a")
        }
    }

    def "direct task dependency still is an IP violation"() {
        given:
        settingsFile << """
            include(":a")
        """
        buildFile << """
            tasks.register("bar")
        """
        file("a/build.gradle") << """
            tasks.register("foo") {
                dependsOn(rootProject.tasks.bar)
            }
        """

        when:
        isolatedProjectsFailsUsing(mode, ":a:foo")

        then:
        fixture.assertIsolatedProjectsProblems(mode) {
            projectsConfigured(":", ":a")
            problem("Build file 'a/build.gradle': line 3: Project ':a' cannot access 'Project.tasks' functionality on another project ':'")
        }

        where:
        mode << ALL_MODES
    }

    def "cannot access task by path from another project with IP enabled"() {
        given:
        settingsFile """
            include 'other'
        """
        buildFile """
            task foobar
        """
        buildFile "other/build.gradle", """
            println($access)
        """

        when:
        isolatedProjectsFailsUsing(mode, "help")

        then:
        fixture.assertIsolatedProjectsProblems(mode) {
            projectsConfigured(":", ":other")
            problem("Build file 'other/build.gradle': line 2: Project ':other' cannot access 'Project.tasks' functionality on another project ':'")
        }

        where:
        access << [
            'tasks.findByPath(":unknown")',
            'tasks.getByPath(":foobar").name',
            'tasks.findByPath(":foobar").name',
        ]

        combined:
        mode << ALL_MODES
    }
}
