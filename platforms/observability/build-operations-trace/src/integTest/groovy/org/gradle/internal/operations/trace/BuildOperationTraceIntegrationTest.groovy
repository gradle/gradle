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
                    problems.reporter.report(problems.groups.compilation.java.problemId("Unused import")) {}
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

    def "directory option writes a separate jsonl file for each invocation"() {
        when:
        run "help", "-D${BuildOperationTrace.DIR_SYSPROP}=traces"

        then:
        def first = jsonlTraces("traces").first()
        postBuildOutputContains("Build operation trace: ${first}")
        file("traces").listFiles().name == [first.name]
        first.length() > 0

        when:
        def firstContent = first.text
        run "help", "-D${BuildOperationTrace.DIR_SYSPROP}=traces"

        then:
        def traces = jsonlTraces("traces")
        traces.size() == 2
        def second = traces[1]
        postBuildOutputContains("Build operation trace: ${second}")
        file("traces").listFiles().name.sort() == [first.name, second.name]
        first.text == firstContent
    }

    def "directory option writes tree files next to the session log"() {
        when:
        run "help", "-D${BuildOperationTrace.TREE_SYSPROP}=true", "-D${BuildOperationTrace.DIR_SYSPROP}=traces"

        then:
        def logs = jsonlTraces("traces")
        logs.size() == 1
        def baseName = logs[0].name - ".jsonl"
        file("traces/${baseName}-tree.txt").exists()
        file("traces/${baseName}-tree.json").exists()
    }

    def "directory option accepts an absolute path"() {
        given:
        def absolutePath = tmpDir.file("custom-traces").absolutePath

        when:
        run "help", "-D${BuildOperationTrace.DIR_SYSPROP}=$absolutePath"

        then:
        tmpDir.file("custom-traces").listFiles().findAll { it.name.endsWith(".jsonl") }.size() == 1
    }

    def "directory option can be set in gradle.properties"() {
        file("gradle.properties") << "${BuildOperationTrace.DIR_SYSPROP}=traces\n"

        when:
        run "help"

        then:
        jsonlTraces("traces").size() == 1
    }

    def "trace path and trace directory cannot be set together"() {
        when:
        fails "help", "-D${BuildOperationTrace.SYSPROP}=trace", "-D${BuildOperationTrace.DIR_SYSPROP}=traces"

        then:
        failureCauseContains("cannot be used together")
    }

    def "false trace value does not block the directory option"() {
        file("gradle.properties") << "${BuildOperationTrace.SYSPROP}=false\n"

        when:
        run "help", "-D${BuildOperationTrace.DIR_SYSPROP}=traces"

        then:
        jsonlTraces("traces").size() == 1
        testDirectory.listFiles().findAll { it.name.endsWith("-log.txt") } == []
    }

    def "false clears a persistent trace directory for one run"() {
        file("gradle.properties") << "${BuildOperationTrace.DIR_SYSPROP}=traces\n"

        when:
        run "help", "-D${BuildOperationTrace.DIR_SYSPROP}=false"

        then:
        !file("traces").exists()
        !file("false").exists()
        testDirectory.listFiles().findAll { it.name.endsWith("-log.txt") || it.name.endsWith(".jsonl") } == []
        outputDoesNotContain("Build operation trace:")
        postBuildOutputDoesNotContain("Build operation trace:")
    }

    def "false trace directory lets one run use the single-file trace"() {
        file("gradle.properties") << "${BuildOperationTrace.DIR_SYSPROP}=traces\n"

        when:
        run "help", "-D${BuildOperationTrace.DIR_SYSPROP}=false", "-D${BuildOperationTrace.SYSPROP}=custom"

        then:
        file("custom-log.txt").exists()
        !file("traces").exists()
        !file("false").exists()
    }

    def "trace directory false does not write into a directory named false"() {
        when:
        run "help", "-D${BuildOperationTrace.DIR_SYSPROP}=false"

        then:
        !file("false").exists()
        outputDoesNotContain("Build operation trace:")
        postBuildOutputDoesNotContain("Build operation trace:")
    }

    def "an empty trace directory is rejected"() {
        when:
        fails "help", "-D${BuildOperationTrace.DIR_SYSPROP}="

        then:
        failureCauseContains("must be a directory path")
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

    private List<File> jsonlTraces(String directory) {
        List<File> traces = file(directory).listFiles().findAll { it.name.endsWith(".jsonl") }
        // «utc-timestamp»-«id».jsonl, where the timestamp is a fixed-width yyyyMMdd-HHmmss-SSS
        traces.each { assert it.name ==~ /\d{8}-\d{6}-\d{3}-[0-9a-z]+\.jsonl/ }
        traces.sort { it.name }
    }
}
