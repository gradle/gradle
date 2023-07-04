/*
 * Copyright 2017 the original author or authors.
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
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture

class CachedTaskActionIntegrationTest extends AbstractIntegrationSpec implements DirectoryBuildCacheFixture {

    def "ad hoc tasks with different actions don't share results"() {
        buildFile << """
            task first {
                outputs.file file("first.txt")
                outputs.cacheIf { true }
                doFirst {
                    file("first.txt").text = "Hello from the first task"
                }
            }

            task second {
                outputs.file file("second.txt")
                outputs.cacheIf { true }
                doFirst {
                    file("second.txt").text = "Hello from the second task"
                }
            }
        """

        when:
        withBuildCache().run "first", "second"
        then:
        executedAndNotSkipped(":first", ":second")
        file("first.txt").text == "Hello from the first task"
        file("second.txt").text == "Hello from the second task"
    }

    def "ad hoc tasks with the same action share results"() {
        file("input.txt").text = "data"
        buildFile << """
            def input = file("input.txt")
            def action = {
                println "Same action"
            }

            task taskA {
                def output = file("build/task-a/output.txt")
                inputs.file input
                outputs.file output
                outputs.cacheIf { true }
                doFirst action
            }

            task taskB {
                def output = file("build/task-b/output.txt")
                inputs.file input
                outputs.file output
                outputs.cacheIf { true }
                doFirst action
            }
        """

        when:
        withBuildCache().run "taskA", "taskB"
        then:
        executedAndNotSkipped(":taskA")
        skipped(":taskB")
    }

    def "built-in tasks with different actions don't share results"() {
        file("src/main/java/Main.java").text = "public class Main {}"
        buildFile << """
            def input = file("input.txt")

            task compileA(type: JavaCompile) {
                destinationDirectory = file("build/compile-a")
                doLast {
                    destinationDirectory.file("output.txt").get().asFile << "From compile task A"
                }
            }

            task compileB(type: JavaCompile) {
                destinationDirectory = file("build/compile-b")
                doLast {
                    destinationDirectory.file("output.txt").get().asFile << "From compile task B"
                }
            }

            tasks.withType(JavaCompile) {
                sourceCompatibility = JavaVersion.current().toString()
                targetCompatibility = JavaVersion.current().toString()
                source "src/main/java"
                classpath = files()
            }
        """

        when:
        withBuildCache().run "compileA", "compileB"
        then:
        executedAndNotSkipped(":compileA", ":compileB")
        file("build/compile-a/output.txt").text == "From compile task A"
        file("build/compile-b/output.txt").text == "From compile task B"
    }
}
