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
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.NormalizeLineEndings


private
const val FLAKY_ANNOTATION = "org.gradle.test.fixtures.Flaky"


private
val SOURCE_EXTENSIONS = setOf("groovy", "java", "kt")


/**
 * Records whether a test source set declares `@Flaky`, by scanning its sources rather than its classpath.
 *
 * A false positive costs one forked JVM that discovers nothing, a false negative drops a flaky test from
 * the only build that runs it, so the check is the crudest one that works: does any source file mention
 * the annotation's fully qualified name? The bare type name would not do - `com.Flaky` appears as a
 * fixture class name in expected JUnit XML.
 *
 * The scan never leaves the source set, so a `@Flaky` method inherited from a base class declared in
 * `testFixtures`, or in the `main` source set of a fixtures project, is not seen. Nothing does that today.
 */
@CacheableTask
abstract class DetectFlakyTests : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:IgnoreEmptyDirectories
    @get:NormalizeLineEndings
    abstract val sources: ConfigurableFileCollection

    @get:OutputFile
    abstract val resultFile: RegularFileProperty

    @TaskAction
    fun detect() {
        val hasFlakyTests = sources.files.any { file ->
            file.isFile && file.extension in SOURCE_EXTENSIONS &&
                file.useLines { lines -> lines.any { FLAKY_ANNOTATION in it } }
        }
        resultFile.get().asFile.writeText(hasFlakyTests.toString())
    }
}
