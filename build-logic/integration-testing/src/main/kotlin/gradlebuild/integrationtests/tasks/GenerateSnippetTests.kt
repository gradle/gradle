/*
 * Copyright 2023 the original author or authors.
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

package gradlebuild.integrationtests.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.util.regex.Pattern
import kotlin.io.path.createDirectories


abstract class GenerateSnippetTests : DefaultTask() {
    private
    val SAMPLE_START = Pattern.compile("""<pre class=['"]autoTested(.*?)['"].*?>""")

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val sources: ConfigurableFileCollection

    @get:Input
    abstract val testClassName: Property<String>

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @TaskAction
    fun scanAndGenerate() {
        if (hasTestableSnippets()) {
            generateTestFile()
        }
    }

    fun generateTestFile() {
        val testFile = output.file("org/gradle/samples/${testClassName.get()}.groovy").get().asFile

        testFile.parentFile.toPath().createDirectories()
        testFile.writeText(
            """
            package org.gradle.samples

            import org.gradle.integtests.fixtures.AbstractAutoTestedSamplesTest
            import org.junit.Test

            class ${testClassName.get()} extends AbstractAutoTestedSamplesTest {
                @Test
                void runSamples() {
                    runSamplesFrom("src/main")
                }
            }
            """.trimIndent()
        )
    }

    private
    fun hasTestableSnippets(): Boolean {
        var hasTestableSnippets = false
        sources.asFileTree.matching {
            include("**/*.java")
            include("**/*.groovy")
        }.visit {
            if (!isDirectory && SAMPLE_START.matcher(file.readText()).find()) {
                hasTestableSnippets = true
                stopVisiting()
            }
        }
        return hasTestableSnippets
    }
}
