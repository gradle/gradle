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

package org.gradle.execution.taskgraph

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class TaskGraphLazyQueriesIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        createDirs("a", "b")
        settingsFile """
            include("a", "b")
        """
    }

    def "isScheduled answers whether a task of the same project is in the task graph"() {
        buildFile("a/build.gradle", """
            def tScheduled = gradle.taskGraph.isScheduled(":a:t")

            tasks.register("t")
            tasks.register("packageX") {
                onlyIf("t is scheduled") { tScheduled.get() }
                doLast { println("packageX ran") }
            }
        """)

        when:
        run ":a:t", ":a:packageX"

        then:
        executed(":a:packageX")
        outputContains("packageX ran")

        when:
        run ":a:packageX"

        then:
        skipped(":a:packageX")
        outputDoesNotContain("packageX ran")
    }

    def "isScheduled answers whether a task of another project is in the task graph"() {
        buildFile("a/build.gradle", """
            tasks.register("t")
        """)
        buildFile("b/build.gradle", """
            abstract class Report extends DefaultTask {
                @Input abstract Property<Boolean> getTaskScheduled()

                @TaskAction
                void report() {
                    println("t scheduled = " + taskScheduled.get())
                }
            }

            tasks.register("report", Report) {
                taskScheduled = gradle.taskGraph.isScheduled(":a:t")
            }
        """)

        when:
        run ":a:t", ":b:report"

        then:
        outputContains("t scheduled = true")

        when:
        run ":b:report"

        then:
        outputContains("t scheduled = false")
    }

    def "isScheduled is answered against the current build when its value is reused"() {
        buildFile("a/build.gradle", """
            def tScheduled = gradle.taskGraph.isScheduled(":a:t")

            tasks.register("t")
            tasks.register("check") {
                doLast { println("t scheduled = " + tScheduled.get()) }
            }
        """)

        when:
        run ":a:t", ":a:check"

        then:
        outputContains("t scheduled = true")

        when:
        run ":a:t", ":a:check"

        then:
        outputContains("t scheduled = true")

        when:
        run ":a:check"

        then:
        outputContains("t scheduled = false")

        when:
        run ":a:check"

        then:
        outputContains("t scheduled = false")
    }

    def "isScheduled treats a task excluded from the command line as not scheduled"() {
        buildFile("a/build.gradle", """
            def tScheduled = gradle.taskGraph.isScheduled(":a:t")

            tasks.register("t")
            tasks.register("check") {
                dependsOn("t")
                doLast { println("t scheduled = " + tScheduled.get()) }
            }
        """)

        when:
        run ":a:check", "-x", ":a:t"

        then:
        outputContains("t scheduled = false")
    }

    def "querying #query during configuration fails"() {
        buildFile("a/build.gradle", """
            tasks.register("t")
            println(gradle.taskGraph.${query}.get())
        """)

        when:
        fails ":a:t"

        then:
        failure.assertHasCause("Cannot query $description before the task graph is ready. Query it during task execution instead, for example from onlyIf { } or as a task input.")

        where:
        query                              | description
        'isScheduled(":a:t")'              | "whether task ':a:t' is scheduled"
        'anyScheduled(DefaultTask)'        | "whether any task of type 'org.gradle.api.DefaultTask' is scheduled"
    }

    def "isScheduled rejects a relative task path"() {
        buildFile("a/build.gradle", """
            gradle.taskGraph.isScheduled("t")
        """)

        when:
        fails "help"

        then:
        failure.assertHasCause("Task path 't' must be absolute, for example ':project:task'.")
    }

    def "anyScheduled matches a task type loaded by another class loader by its name"() {
        // Each build script is compiled into its own class loader,
        // so the two `Lint` classes have the same name but are different classes.
        buildFile("a/build.gradle", """
            class Lint extends DefaultTask {}

            tasks.register("lint", Lint)
        """)
        buildFile("b/build.gradle", """
            class Lint extends DefaultTask {}

            def lintScheduled = gradle.taskGraph.anyScheduled(Lint)

            tasks.register("report") {
                doLast { println("lint scheduled = " + lintScheduled.get()) }
            }
        """)

        when:
        run ":a:lint", ":b:report"

        then:
        outputContains("lint scheduled = true")

        when:
        run ":b:report"

        then:
        outputContains("lint scheduled = false")
    }

    def "anyScheduled matches subtypes and interfaces of the registered task type"() {
        buildFile("a/build.gradle", """
            interface Verification {}
            class Lint extends DefaultTask implements Verification {}
            class StrictLint extends Lint {}

            def lintScheduled = gradle.taskGraph.anyScheduled(Lint)
            def verificationScheduled = gradle.taskGraph.anyScheduled(Verification)
            def testScheduled = gradle.taskGraph.anyScheduled(Test)

            tasks.register("strictLint", StrictLint)
            tasks.register("report") {
                doLast {
                    println("lint scheduled = " + lintScheduled.get())
                    println("verification scheduled = " + verificationScheduled.get())
                    println("test scheduled = " + testScheduled.get())
                }
            }
        """)

        when:
        run ":a:strictLint", ":a:report"

        then:
        outputContains("lint scheduled = true")
        outputContains("verification scheduled = true")
        outputContains("test scheduled = false")
    }

    def "anyScheduled can feed a command line argument provider of a test task"() {
        buildFile("a/build.gradle", """
            plugins { id("java") }

            ${mavenCentralRepository()}
            dependencies { testImplementation("junit:junit:4.13.2") }

            abstract class CoverageAgentArgs implements CommandLineArgumentProvider {
                @Input abstract Property<Boolean> getCoverageScheduled()

                @Override
                Iterable<String> asArguments() {
                    coverageScheduled.get() ? ["-Dcoverage=on"] : ["-Dcoverage=off"]
                }
            }

            class CoverageReport extends DefaultTask {}
            tasks.register("coverageReport", CoverageReport)

            tasks.named("test", Test) {
                jvmArgumentProviders.add(objects.newInstance(CoverageAgentArgs).tap {
                    coverageScheduled = gradle.taskGraph.anyScheduled(CoverageReport)
                })
                testLogging.showStandardStreams = true
            }
        """)
        file("a/src/test/java/CoverageTest.java") << """
            public class CoverageTest {
                @org.junit.Test
                public void printsCoverage() {
                    System.out.println("coverage=" + System.getProperty("coverage"));
                }
            }
        """

        when:
        run ":a:test", ":a:coverageReport"

        then:
        outputContains("coverage=on")

        when:
        run ":a:test", "--rerun"

        then:
        outputContains("coverage=off")
    }
}
