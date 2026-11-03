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

import gradlebuild.basics.BuildParams.FLAKY_TEST
import gradlebuild.basics.BuildParams.PROPERTY_BASED_TESTS
import gradlebuild.basics.TestSelectionStrategy
import gradlebuild.basics.capitalize
import gradlebuild.basics.flakyTestStrategy
import gradlebuild.basics.propertyBasedTestStrategy
import gradlebuild.testing.tasks.DetectAnnotatedTests
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import java.io.File


/**
 * A build mode that runs only the tests with some annotation.
 */
internal
enum class AnnotatedTestSelection(
    val annotation: String,
    /**
     * What [DetectAnnotatedTests] searches for, a fully qualified name or a package.
     */
    val marker: String,
    private val taskNamePrefix: String,
    val resultDir: String,
) {
    FLAKY(
        "@Flaky",
        "org.gradle.test.fixtures.Flaky",
        "detectFlaky",
        "flaky-tests",
    ),
    PROPERTY_BASED(
        "@HegelTest",
        // The package rather than the annotation, so that wildcard imports are detected too.
        // Every Hegel test imports something from it, at least dev.hegel.TestCase.
        "dev.hegel",
        "detectPropertyBased",
        "property-based-tests",
    );

    fun detectTaskName(sourceSet: SourceSet): String =
        "$taskNamePrefix${sourceSet.name.capitalize()}"
}


/**
 * The selection of `-PflakyTests=ONLY` or `-PpropertyBasedTests=ONLY`, if either is set.
 */
internal
val Project.configuredAnnotatedTestSelection: AnnotatedTestSelection?
    get() {
        val flakyOnly = flakyTestStrategy == TestSelectionStrategy.ONLY
        val propertyBasedOnly = propertyBasedTestStrategy == TestSelectionStrategy.ONLY
        return when {
            flakyOnly && propertyBasedOnly ->
                throw GradleException("-P$FLAKY_TEST=only and -P$PROPERTY_BASED_TESTS=only can't be combined.")
            flakyOnly -> AnnotatedTestSelection.FLAKY
            propertyBasedOnly -> AnnotatedTestSelection.PROPERTY_BASED
            else -> null
        }
    }


/**
 * Under `-PflakyTests=ONLY` or `-PpropertyBasedTests=ONLY`, registers a [DetectAnnotatedTests] task per test source set
 * so that [Test] tasks whose source set has no test with the annotation can be skipped instead of forking a JVM to
 * discover that.
 */
fun Project.configureTestSelection() {
    val selection = configuredAnnotatedTestSelection ?: return
    val sourceSets = extensions.findByType<SourceSetContainer>() ?: return
    sourceSets.configureEach {
        if (shouldScanForSelectedTests()) {
            registerDetectAnnotatedTestsTask(this, selection)
        }
    }
    tasks.named<Test>("test") {
        skipWhenNoSelectedTests(sourceSets.getByName("test"))
    }
}


internal
fun SourceSet.shouldScanForSelectedTests(): Boolean =
    name == "test" || name.endsWith("Test")


/**
 * Skips this task when [sourceSet] has no test with the annotation of `-PflakyTests=ONLY` or
 * `-PpropertyBasedTests=ONLY`. Does nothing unless one of them is set.
 *
 * The scan only decides whether the task is skipped. It doesn't add class name filters for the classes it found,
 * because `CrossVersionTestEngine` creates a test per Gradle version, and class name filters drop those tests
 * without an error.
 */
fun Test.skipWhenNoSelectedTests(sourceSet: SourceSet) {
    val selection = project.configuredAnnotatedTestSelection ?: return
    val taskName = selection.detectTaskName(sourceSet)
    require(project.tasks.names.contains(taskName)) {
        "Task '$taskName' is not registered in ${project.path}. Source set '${sourceSet.name}' is not scanned " +
            "for ${selection.annotation} tests, either because gradlebuild.unittest-and-compile is not applied or because " +
            "the source set name does not look like a test source set."
    }
    val detectTask = project.tasks.named<DetectAnnotatedTests>(taskName)
    val resultFile = detectTask.flatMap { it.resultFile }
    val scannedClassesDirs = sourceSet.output.classesDirs
    dependsOn(detectTask)
    onlyIf("has ${selection.annotation} tests") { task ->
        val test = task as Test
        if (test.testClassesDirs.files != scannedClassesDirs.files) {
            // Repointed at another source set's classes after this was wired - see
            // testing/precondition-tester - so the scan is invalid. Assume it should run.
            true
        } else {
            readDetectionResult(resultFile.get().asFile)
        }
    }
}


private
fun Project.registerDetectAnnotatedTestsTask(sourceSet: SourceSet, selection: AnnotatedTestSelection) {
    val taskName = selection.detectTaskName(sourceSet)
    if (tasks.names.contains(taskName)) {
        return
    }
    tasks.register<DetectAnnotatedTests>(taskName) {
        description = "Records whether the ${sourceSet.name} source set has ${selection.annotation} tests"
        marker.set(selection.marker)
        sources.from(sourceSet.sourceCode())
        resultFile.convention(layout.buildDirectory.file("${selection.resultDir}/${sourceSet.name}.txt"))
    }
}


/**
 * The source set's code, without its resources: [SourceSet.getAllSource] would include the hundreds of
 * `.java` and `.groovy` fixtures under `src/<test source set>/resources`, creating potential false positives.
 */
private
fun SourceSet.sourceCode(): List<Any> =
    listOfNotNull(java, extensions.findByName("groovy"), extensions.findByName("kotlin"))


private
fun readDetectionResult(resultFile: File): Boolean {
    check(resultFile.isFile) {
        "$resultFile is missing, but it is produced by a task this test task depends on. " +
            "Refusing to treat that as 'no annotated tests'."
    }
    return resultFile.readText().trim().toBoolean()
}
