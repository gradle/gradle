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

package gradlebuild.testing

import gradlebuild.basics.FlakyTestStrategy
import gradlebuild.basics.capitalize
import gradlebuild.basics.flakyTestStrategy
import gradlebuild.testing.tasks.DetectFlakyTests
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import java.io.File


/**
 * When `-PflakyTests=ONLY`, register a cacheable detect task per test source set so that [Test] tasks
 * can be skipped when their source set declares no `@Flaky` tests.
 *
 * Detection looks at the source set's source files only - not at its compile or runtime classpath - so
 * a subproject without flaky tests never forks a test JVM to discover that it has nothing to run.
 */
fun Project.configureFlakyTestSelection() {
    if (flakyTestStrategy != FlakyTestStrategy.ONLY) {
        return
    }
    val sourceSets = extensions.findByType<SourceSetContainer>() ?: return
    sourceSets.configureEach {
        if (shouldScanForFlakyTests()) {
            registerDetectFlakyTestsTask(this)
        }
    }
    tasks.named<Test>("test") {
        skipWhenNoFlakyTests(sourceSets.getByName("test"))
    }
}


internal
fun SourceSet.shouldScanForFlakyTests(): Boolean =
    name == "test" || name.endsWith("Test")


internal
fun detectFlakyTestsTaskName(sourceSet: SourceSet): String =
    "detectFlaky${sourceSet.name.capitalize()}"


private
fun Project.registerDetectFlakyTestsTask(sourceSet: SourceSet) {
    val taskName = detectFlakyTestsTaskName(sourceSet)
    if (tasks.names.contains(taskName)) {
        return
    }
    tasks.register<DetectFlakyTests>(taskName) {
        description = "Records whether the ${sourceSet.name} source set declares @Flaky tests"
        sources.from(sourceSet.sourceCode())
        resultFile.convention(layout.buildDirectory.file("flaky-tests/${sourceSet.name}.txt"))
    }
}


/**
 * Skips this task when [sourceSet] declares no `@Flaky` test. Does nothing unless `-PflakyTests=ONLY`.
 *
 * Only this yes or no is used. Narrowing the task's own test filters as well looks tempting, but on
 * `gradle<version>CrossVersionTest` it silently drops executions: `CrossVersionTestEngine` synthesizes
 * a variant per TAPI/target pair, each its own discovery pass with its own classloader, and name-based
 * filtering does not survive that - 1127 executions against 1297. Which tests run stays the job of the
 * `@Flaky` tag and annotation filters.
 */
fun Test.skipWhenNoFlakyTests(sourceSet: SourceSet) {
    if (project.flakyTestStrategy != FlakyTestStrategy.ONLY) {
        return
    }
    val taskName = detectFlakyTestsTaskName(sourceSet)
    require(project.tasks.names.contains(taskName)) {
        // Silently doing nothing here would leave the task forking a JVM to discover nothing, which is
        // exactly what this mechanism exists to avoid, so say so instead.
        "Task '$taskName' is not registered in ${project.path}. Source set '${sourceSet.name}' is not scanned " +
            "for @Flaky tests, either because gradlebuild.unittest-and-compile is not applied or because " +
            "the source set name does not look like a test source set."
    }
    val detectTask = project.tasks.named<DetectFlakyTests>(taskName)
    val resultFile = detectTask.flatMap { it.resultFile }
    val scannedClassesDirs = sourceSet.output.classesDirs
    dependsOn(detectTask)
    onlyIf("has flaky tests") { task ->
        val test = task as Test
        if (test.testClassesDirs.files != scannedClassesDirs.files) {
            // The task was repointed at another source set's classes after this was wired - see
            // testing/precondition-tester, which runs its `test` classes from every DistributionTest.
            // The scan does not describe what this task runs, so it cannot answer the question.
            true
        } else {
            readHasFlakyTests(resultFile.get().asFile)
        }
    }
}


/**
 * The source set's code, without its resources.
 *
 * [SourceSet.getAllSource] bundles the resource directories in, and this build keeps well over a hundred
 * `.java` and `.groovy` files under `src/<test source set>/resources` as fixtures for the tests that use
 * them. A fixture that merely mentions the annotation would otherwise keep every task running.
 */
private
fun SourceSet.sourceCode(): List<Any> =
    listOfNotNull(java, extensions.findByName("groovy"), extensions.findByName("kotlin"))


private
fun readHasFlakyTests(resultFile: File): Boolean {
    check(resultFile.isFile) {
        // The file is the output of a task this one depends on, so a missing file means something went
        // wrong. Reading it as "no flaky tests" would skip the task and report a green build that ran
        // none of the tests it exists to run.
        "$resultFile is missing, but it is produced by a task this test task depends on. " +
            "Refusing to treat that as 'no flaky tests'."
    }
    return resultFile.readText().trim().toBoolean()
}
