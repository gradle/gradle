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

package org.gradle.api.internal.tasks.execution.durations

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class TaskDurationRecordingIntegrationTest extends AbstractIntegrationSpec {

    def historyFile = file(".gradle/task-durations.txt")

    def "records successful executions with their duration"() {
        given:
        buildFile << """
            tasks.register("slow") {
                doLast { Thread.sleep(200) }
            }
        """

        when:
        succeeds "slow"

        then:
        entries(":slow").size() == 1
        (entries(":slow")[0] as long) >= 200
    }

    def "records up-to-date tasks as taking no time"() {
        given:
        buildFile << """
            def out = layout.buildDirectory.file("out.txt")
            tasks.register("produce") {
                outputs.file(out)
                doLast { out.get().asFile.text = "x" }
            }
        """

        when:
        succeeds "produce"
        succeeds "produce"

        then:
        skipped ":produce"
        entries(":produce").size() == 2
        entries(":produce")[1] == "0"
    }

    def "records tasks loaded from the build cache as taking no time"() {
        given:
        buildFile << """
            def out = layout.buildDirectory.file("out.txt")
            tasks.register("produce") {
                outputs.file(out)
                outputs.cacheIf { true }
                doLast { out.get().asFile.text = "x" }
            }
        """

        when:
        succeeds "produce", "--build-cache"
        file("build").deleteDir()
        succeeds "produce", "--build-cache"

        then:
        outputContains("> Task :produce FROM-CACHE")
        entries(":produce").size() == 2
        entries(":produce")[1] == "0"
    }

    def "records skipped tasks as taking no time"() {
        given:
        buildFile << """
            tasks.register("skip") {
                onlyIf { false }
                doLast { }
            }
        """

        when:
        succeeds "skip"

        then:
        outputContains("> Task :skip SKIPPED")
        entries(":skip") == ["0"]
    }

    def "records tasks without sources as taking no time"() {
        given:
        buildFile << """
            tasks.register("process") {
                inputs.files(fileTree("src")).skipWhenEmpty()
                doLast { }
            }
        """

        when:
        succeeds "process"

        then:
        result.assertTaskSkipped(":process")
        outputContains("> Task :process NO-SOURCE")
        entries(":process") == ["0"]
    }

    def "does not record failed tasks"() {
        given:
        buildFile << """
            def ok = tasks.register("ok") { doLast { } }
            tasks.register("broken") {
                dependsOn ok
                doLast { throw new RuntimeException("boom") }
            }
        """

        when:
        fails "broken"

        then:
        entries(":ok").size() == 1
        entries(":broken").empty
    }

    def "keeps the 3 most recent durations"() {
        given:
        buildFile << """
            tasks.register("hello") { doLast { } }
        """

        when:
        4.times { succeeds "hello" }

        then:
        entries(":hello").size() == 3
    }

    def "records tasks of all projects in a parallel build"() {
        given:
        createDirs("a", "b", "c")
        settingsFile << """
            include "a", "b", "c"
        """
        buildFile << """
            allprojects {
                tasks.register("hello") { doLast { } }
            }
        """

        when:
        succeeds "hello", "--parallel"

        then:
        [":hello", ":a:hello", ":b:hello", ":c:hello"].every { entries(it).size() == 1 }
    }

    def "keys tasks of included builds by build tree path"() {
        given:
        settingsFile << """
            includeBuild "lib"
        """
        file("lib/settings.gradle") << ""
        file("lib/build.gradle") << """
            tasks.register("hello") { doLast { } }
        """

        when:
        succeeds ":lib:hello"

        then:
        entries(":lib:hello").size() == 1
    }

    private List<String> entries(String taskPath) {
        def line = historyFile.readLines().find { it.startsWith(taskPath + "\t") }
        line == null ? [] : line.substring(taskPath.length() + 1).split(",") as List
    }
}
