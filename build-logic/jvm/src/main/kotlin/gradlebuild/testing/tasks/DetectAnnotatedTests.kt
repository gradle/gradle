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

package gradlebuild.testing.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.NormalizeLineEndings


private
val SOURCE_EXTENSIONS = setOf("groovy", "java", "kt")


/**
 * Records whether a test source set mentions [marker], by scanning its sources rather than its classpath.
 *
 * Some test tasks run only the tests with an annotation, like `@Flaky`.
 * Such a task uses this result to skip itself when no test in its source set has the annotation.
 * The check is a plain text search of the source files. This doesn't try to do any smart filtering, because
 * a false negative means lost coverage, whereas a false positive just means a few extra seconds of test
 * execution.
 *
 * Use a fully qualified name or a package as the marker to minimize false positives. Using a package can
 * be useful to match wildcard imports of the annotation as well.
 *
 * The scan never leaves the source set, so an annotated method inherited from a base class declared in
 * `testFixtures`, or in the `main` source set of a fixtures project, is not detected. This is currently not
 * an issue.
 */
@CacheableTask
abstract class DetectAnnotatedTests : DefaultTask() {

    @get:Input
    abstract val marker: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:IgnoreEmptyDirectories
    @get:NormalizeLineEndings
    abstract val sources: ConfigurableFileCollection

    @get:OutputFile
    abstract val resultFile: RegularFileProperty

    @TaskAction
    fun detect() {
        val marker = marker.get()
        val found = sources.files.any { file ->
            file.isFile && file.extension in SOURCE_EXTENSIONS &&
                file.useLines { lines -> lines.any { marker in it } }
        }
        resultFile.get().asFile.writeText(found.toString())
    }
}
