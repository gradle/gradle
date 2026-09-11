/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.operations.trace


import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationTreeFixture
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule

class BuildOperationTraceIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def "produces a valid trace"() {
        when:
        run "help", "-D${BuildOperationTrace.SYSPROP}=trace"

        then:
        file("trace-log.txt").exists()

        and:
        postBuildOutputContains("Build operation trace:")

        and:
        def tree = BuildOperationTrace.readTree(file("trace").path)
        def fixture = new BuildOperationTreeFixture(tree)
        fixture.roots.first().displayName == "Run build"
        fixture.only("Configure project :")
    }

    def "trace stays readable when a problem in a predefined group is reported"() {
        // Predefined problem groups expose their children, and children expose their parent. The trace
        // serializes progress details by walking getters, so groups need explicit serialization to avoid a cycle.
        given:
        buildFile """
            import org.gradle.api.problems.Problems

            abstract class ReportProblem extends DefaultTask {
                @Inject
                abstract Problems getProblems()

                @TaskAction
                void run() {
                    problems.reporter.report(problems.groups.compilation.java.problem("Unused import")) {}
                }
            }

            tasks.register("reportProblem", ReportProblem)
        """

        when:
        run "reportProblem", "-D${BuildOperationTrace.SYSPROP}=trace"

        then:
        def tree = BuildOperationTrace.readTree(file("trace").path)
        def problemEvents = new BuildOperationTreeFixture(tree).records.collectMany { it.progress }
            .findAll { it.detailsClassName == "org.gradle.api.problems.internal.DefaultProblemProgressDetails" }
            .collect { it.details.problem }
        def problem = problemEvents.find { it.definition.id.name == "Unused import" }
        problem != null
        def group = problem.definition.id.group
        group.name == "Java"
        group.displayName == "Java"
        group.description == "Java code compilation, including the compiler's configuration, compiler invocation, or compiler plugins."
        group.parent.name == "Compilation"
        group.parent.displayName == "Compilation"
        group.parent.description == "Code compilation, including the compiler's configuration, compiler invocation, or compiler plugins."
        group.parent.parent == null
        group.keySet() == ["name", "displayName", "description", "parent"] as Set
    }

    def "produces operations trace when no path is provided"() {
        when:
        run "help", "-D${BuildOperationTrace.SYSPROP}="

        then:
        file("operations-log.txt").exists()
    }

    def "no tree files are produced by default"() {
        when:
        run "help", "-D${BuildOperationTrace.SYSPROP}=trace"

        then:
        file("trace-log.txt").exists()
        !file("trace-tree.txt").exists()
        !file("trace-tree.json").exists()
    }

    def "no trace files are produced when trace parameter is false"() {
        when:
        run "help", "-D${BuildOperationTrace.SYSPROP}=false"

        then:
        testDirectory.listFiles().findAll { it.name.endsWith("-log.txt") } == []

        and:
        outputDoesNotContain("Build operation trace:")
        postBuildOutputDoesNotContain("Build operation trace:")
    }

    def "trace files are written to absolute path when absolute path is provided"() {
        given:
        def absolutePath = tmpDir.file("custom-trace").absolutePath

        when:
        run "help", "-D${BuildOperationTrace.TREE_SYSPROP}=true", "-D${BuildOperationTrace.SYSPROP}=$absolutePath"

        then:
        tmpDir.file("custom-trace-log.txt").exists()
        tmpDir.file("custom-trace-tree.txt").exists()
        tmpDir.file("custom-trace-tree.json").exists()
    }

    def "when running from subdirectory, trace files are relative to the root directory for #description parameter"() {
        // Explicit settings file to ensure test directory is the root directory of the build
        settingsFile """
            rootProject.name = "root"
            include("sub")
        """
        createDirs("sub")

        when:
        inDirectory "sub"
        run "help", "-D${BuildOperationTrace.TREE_SYSPROP}=true", "-D${BuildOperationTrace.SYSPROP}=$trace"

        then:
        file("$trace-log.txt").exists()
        file("$trace-tree.txt").exists()
        file("$trace-tree.json").exists()

        where:
        description       | trace
        "a file name"     | "custom"
        "a relative path" | "build/custom"
    }

    def "trace parameters can be provided in gradle.properties as #description"() {
        file("gradle.properties") << """
            ${BuildOperationTrace.SYSPROP}=$trace
            ${BuildOperationTrace.TREE_SYSPROP}=true
        """

        when:
        run "help"

        then:
        file("$trace-log.txt").exists()
        file("$trace-tree.txt").exists()
        file("$trace-tree.json").exists()

        where:
        description       | trace
        "a file name"     | "custom"
        "a relative path" | "build/custom"
    }
}
