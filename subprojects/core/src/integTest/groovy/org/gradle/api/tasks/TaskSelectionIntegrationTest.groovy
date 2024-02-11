/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.tasks


import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class TaskSelectionIntegrationTest extends AbstractIntegrationSpec {
    def "given an unqualified name traverse project tree from current project and select all tasks with matching name"() {
        createDirs("a", "b", "a/a", "b/b")
        settingsFile << "include 'a', 'b', 'a:a', 'b:b'"

        buildFile << """
            subprojects {
                task thing
                projectDir.mkdirs()
            }
            """

        when:
        run "thing"

        then:
        result.assertTasksExecuted(":a:thing", ":b:thing", ":a:a:thing", ":b:b:thing")

        when:
        executer.inDirectory(file("a"))
        run "thing"

        then:
        result.assertTasksExecuted(":a:thing", ":a:a:thing")

        // camel case matching
        when:
        run "th"

        then:
        result.assertTasksExecuted(":a:thing", ":b:thing", ":a:a:thing", ":b:b:thing")
    }

    def "stops traversing sub-projects when task implies sub-projects"() {
        createDirs("a", "b", "a/a", "b/b")
        settingsFile << "include 'a', 'b', 'a:a', 'b:b'"

        buildFile << """
            subprojects {
                task thing
                projectDir.mkdirs()
            }
            project(":a") {
                thing.impliesSubProjects = true
            }
            """

        when:
        run "thing"

        then:
        result.assertTasksExecuted(":a:thing", ":b:thing", ":b:b:thing")

        when:
        executer.inDirectory(file("a"))
        run "thing"

        then:
        result.assertTasksExecuted(":a:thing")

        // camel case matching
        when:
        run "th"

        then:
        result.assertTasksExecuted(":a:thing", ":b:thing", ":b:b:thing")
    }

    def "can use camel-case for all segments of qualified task name"() {
        createDirs("child", "child/child")
        settingsFile << "include 'child', 'child:child'"

        buildFile << """
allprojects { task thing }
"""
        when:
        run "chi:chi:th"

        then:
        result.assertTasksExecuted(":child:child:thing")
    }

    def "can use camel case to match software model tasks"() {
        buildFile << """
            model {
                tasks {
                    "sayHelloToUser"(DefaultTask) {
                    }
                }
            }
        """
        when:
        run "sHTU"

        then:
        result.assertTasksExecuted(":sayHelloToUser")
    }

    def "executes project default tasks when none specified"() {
        createDirs("a")
        settingsFile << "include 'a'"

        buildFile << """
    allprojects {
        task a
        task b
    }
    b.dependsOn a
    defaultTasks 'b'
"""
        when:
        run()

        then:
        result.assertTasksExecuted(":a", ":b", ":a:b")
    }
}
