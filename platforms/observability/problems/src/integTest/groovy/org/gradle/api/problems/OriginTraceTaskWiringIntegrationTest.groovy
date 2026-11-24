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

package org.gradle.api.problems

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.problems.ReceivedProblem
import org.gradle.internal.code.Locality
import org.hamcrest.Matchers

/**
 * A problem with two causes (UC12): a task reads what another task writes without depending on it.
 * Neither task is wrong alone, so the origin has one trace per task.
 */
class OriginTraceTaskWiringIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        enableProblemsApiCheck()
        settingsFile """
            include "app"
        """
        file("app/build.gradle") << """
            abstract class GenerateTask extends DefaultTask {
                @OutputDirectory
                abstract DirectoryProperty getOutputDir()

                @TaskAction
                void run() {
                    outputDir.file("generated.txt").get().asFile.text = "generated"
                }
            }

            abstract class ReportTask extends DefaultTask {
                @InputFiles
                abstract ConfigurableFileCollection getSource()

                @OutputFile
                abstract RegularFileProperty getReport()

                @TaskAction
                void run() {
                    report.get().asFile.text = "report"
                }
            }
        """
    }

    def "UC12: a task that reads another task's output without depending on it has one origin trace per task"() {
        given:
        file("app/build.gradle") << """
            tasks.register("generate", GenerateTask) {
                outputDir = layout.buildDirectory.dir("generated")
            }
            tasks.register("report", ReportTask) {
                // A plain file, so Gradle can't tell that 'generate' produces it
                source.from(file("build/generated"))
                report = layout.buildDirectory.file("report.txt")
            }
        """

        when:
        fails(first, second)

        then: "the origin is the same whichever task notices: what the consumer reads, and what the producer writes"
        def problems = implicitDependencyProblems()
        !problems.empty
        problems.each { problem ->
            assert problem.originTraces*.frames*.displayName == [
                [
                    "input property source",
                    "task :app:report",
                    file("app/build.gradle").absolutePath
                ],
                [
                    "output property outputDir",
                    "task :app:generate",
                    file("app/build.gradle").absolutePath
                ]
            ]
            assert problem.originTraces*.frames*.locality.flatten().every { it == Locality.LOCAL }
        }

        and: "Gradle notices when the second of the two is about to run, so the detection is that task"
        def detections = problems.collect { it.detectionTrace.frames*.displayName }
        if (GradleContextualExecuter.configCache) {
            // The two tasks can run in parallel, and then either of them, or both, notice
            assert detections.every { it in [detectionIn(first), detectionIn(second)] }
        } else {
            assert detections == [detectionIn(second)]
        }

        where:
        first           | second
        ":app:generate" | ":app:report"
        ":app:report"   | ":app:generate"
    }

    def "a file given to a task without a property name leaves the task as the leaf"() {
        given:
        file("app/build.gradle") << """
            tasks.register("generate", GenerateTask) {
                outputDir = layout.buildDirectory.dir("generated")
            }
            tasks.register("report") {
                inputs.files(file("build/generated"))
                outputs.file(layout.buildDirectory.file("report.txt"))
                doLast {}
            }
        """

        when:
        fails(":app:generate", ":app:report")

        then:
        def problems = implicitDependencyProblems()
        !problems.empty
        problems.each { problem ->
            assert problem.originTraces*.frames*.displayName == [
                [
                    "task :app:report",
                    file("app/build.gradle").absolutePath
                ],
                [
                    "output property outputDir",
                    "task :app:generate",
                    file("app/build.gradle").absolutePath
                ]
            ]
        }
    }

    def "the origin names both properties when the two locations overlap without being equal"() {
        given:
        file("app/build.gradle") << """
            tasks.register("generate", GenerateTask) {
                outputDir = layout.buildDirectory.dir("generated")
            }
            tasks.register("report", ReportTask) {
                // A file inside the directory that 'generate' writes: the paths differ, so the trace can't match them by equality
                source.from(file("build/generated/generated.txt"))
                report = layout.buildDirectory.file("report.txt")
            }
        """

        when:
        fails(":app:generate", ":app:report")

        then:
        def problems = implicitDependencyProblems()
        !problems.empty
        problems.each { problem ->
            assert problem.originTraces*.frames*.displayName*.first() == [
                "input property source",
                "output property outputDir"
            ]
        }
    }

    private static List<String> detectionIn(String taskPath) {
        ["task $taskPath".toString(), "project :app", "build :"]
    }

    private List<ReceivedProblem> implicitDependencyProblems() {
        failure.assertThatAllDescriptions(Matchers.startsWith("A problem was found with the configuration of task"))
        (0..<receivedProblems.size())
            .collect { receivedProblem(it) }
            .findAll { it.definition.id.fqid == "Gradle:Build Logic:Property has implicit dependency" }
    }
}
