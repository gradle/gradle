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

package gradlebuild.performance.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Fails the build when fewer upstream performance test buckets reported results than expected.
 *
 * A dedicated task — not a check inside [PerformanceTestReport] — so the build outcome is decided
 * by a step independent of the report task's input-tracking and skip semantics. It runs after the
 * report and its zip finalizer so a partial run still publishes `performance-test-results.zip`.
 */
@DisableCachingByDefault(because = "Fast filesystem check; output is the build outcome, not a file")
abstract class VerifyPerformanceBuckets : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val performanceResultsDirectory: DirectoryProperty

    @get:Input
    abstract val expectedBuckets: Property<Int>

    @TaskAction
    fun verify() {
        val expected = expectedBuckets.get()
        val resultsDir = performanceResultsDirectory.get().asFile
        val reported =
            (resultsDir.listFiles()?.filter { it.isDirectory && it.listFiles { _, name -> name.endsWith(".json") }?.isNotEmpty() == true } ?: emptyList())
                .map { it.name }
                .toSet()
        if (reported.size < expected) {
            throw GradleException(
                "Only ${reported.size} of $expected expected performance test buckets reported results in $resultsDir. " +
                    "The aggregate report has been generated from the available data; failing the build so the missing buckets are not silently ignored."
            )
        }
    }
}
