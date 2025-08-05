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
package gradlebuild.integrationtests.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileTreeElement
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.util.regex.Pattern
import javax.inject.Inject

/**
 * This task scans the main source set and findd samples in javadoc with `class='autoTested'`,
 * then generates a subclass of AbstractAutoTestedSamplesTest for each class including samples and adds them to the integTest.
 *
 * For example, we have two classes ABC.java and XYZ.groovy that include `class='autoTested'`,
 * there will be two files generated: `ABCAutoTestedSamplesTest.groovy` and `XYZAutoTestedSamplesTest.groovy`
 */
@CacheableTask
abstract class GenerateAutoTestedSamplesTestTask @Inject constructor(@Internal val fileOperations: FileOperations) : DefaultTask() {
    private
    val sampleStart = Pattern.compile("""<pre class=['"]autoTested(.*?)['"].*?>""")

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val mainSources: ConfigurableFileCollection

    @get:Input
    abstract val generateAutoTestedSamplesTest: Property<Boolean>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    init {
        outputDir.convention(project.layout.buildDirectory.dir("generated/sources/autoTested/groovy"))
    }

    @TaskAction
    fun generate() {
        fileOperations.delete(outputDir.get().asFile)

        if (generateAutoTestedSamplesTest.get()) {
            mainSources.asFileTree.matching {
                include("**/*.java")
                include("**/*.groovy")
            }.visit {
                if (!isDirectory) {
                    generateForFile(this)
                }
            }
        }
    }

    private fun generateForFile(file: FileTreeElement) {
        val fileContent = file.file.readText()
        val relativePath = file.relativePath.toString()
        require(!fileContent.contains("'''")) {
            "The class with class='autoTested' can't contains triple quotes: $relativePath"
        }
        if (!sampleStart.matcher(fileContent).find()) {
            return
        }
        val className = relativePath.substringAfterLast("/").toString().replace(".groovy", "AutoTestedSamplesTest").replace(".java", "AutoTestedSamplesTest")
        val targetFilePath = "${relativePath.substringBeforeLast("/")}/${className}.groovy"
        val targetFile = outputDir.file(targetFilePath).get().asFile
        targetFile.parentFile.mkdirs()
        targetFile.writeText(
            """
package org.gradle.samples
import org.gradle.integtests.fixtures.AbstractAutoTestedSamplesTest
import org.junit.Test

class ${className} extends AbstractAutoTestedSamplesTest {
    private static final String FILE_CONTENT = '''
        ${fileContent}
    '''
    @Test
    void runSamples() {
        runSamplesFromFile(new File('${file.file.absolutePath.replace("\\", "/")}'), FILE_CONTENT)
    }
}
"""
        )
    }
}
