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
 * Under `-PflakyTests=ONLY`, registers a [DetectFlakyTests] task per test source set so that [Test] tasks
 * whose source set declares no `@Flaky` test can be skipped instead of forking a JVM to discover that.
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
 * Only this yes or no is used. Narrowing the task's own filters as well silently drops executions on
 * `gradle<version>CrossVersionTest`, where `CrossVersionTestEngine` synthesizes a variant per TAPI/target
 * pair and name-based filtering does not survive the per-variant discovery pass.
 */
fun Test.skipWhenNoFlakyTests(sourceSet: SourceSet) {
    if (project.flakyTestStrategy != FlakyTestStrategy.ONLY) {
        return
    }
    val taskName = detectFlakyTestsTaskName(sourceSet)
    require(project.tasks.names.contains(taskName)) {
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
            // Repointed at another source set's classes after this was wired - see
            // testing/precondition-tester - so the scan does not describe what this task runs.
            true
        } else {
            readHasFlakyTests(resultFile.get().asFile)
        }
    }
}


/**
 * The source set's code, without its resources: [SourceSet.getAllSource] would bundle in the hundreds of
 * `.java` and `.groovy` fixtures under `src/<test source set>/resources`, any one of which mentioning the
 * annotation would keep every task running.
 */
private
fun SourceSet.sourceCode(): List<Any> =
    listOfNotNull(java, extensions.findByName("groovy"), extensions.findByName("kotlin"))


private
fun readHasFlakyTests(resultFile: File): Boolean {
    check(resultFile.isFile) {
        "$resultFile is missing, but it is produced by a task this test task depends on. " +
            "Refusing to treat that as 'no flaky tests'."
    }
    return resultFile.readText().trim().toBoolean()
}
