/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.provider

import org.gradle.integtests.fixtures.KotlinScriptIntegrationTest
import org.gradle.util.Requires
import spock.lang.Ignore

import static PropertyStateProjectUnderTest.OUTPUT_FILE_CONTENT
import static org.gradle.util.TestPrecondition.KOTLIN_SCRIPT
import static org.gradle.util.TextUtil.normaliseFileSeparators

@Ignore("Fails on CI for unknown reason")
@Requires(KOTLIN_SCRIPT)
class PropertyStateKotlinScriptIntegrationTest extends KotlinScriptIntegrationTest {

    private final PropertyStateProjectUnderTest projectUnderTest = new PropertyStateProjectUnderTest(testDirectory)

    def "can create and use property state in Kotlin-based build script"() {
        given:
        withKotlinBuildSrc()
        file("buildSrc/src/main/kotlin/MyTask.kt") << """
            import org.gradle.api.DefaultTask
            import org.gradle.api.file.FileCollection
            import org.gradle.api.file.ConfigurableFileCollection
            import org.gradle.api.provider.PropertyState
            import org.gradle.api.tasks.TaskAction
            import org.gradle.api.tasks.Input
            import org.gradle.api.tasks.OutputFiles

            open class MyTask : DefaultTask() {
                val enabled: PropertyState<Boolean> = project.property(Boolean::class.java)
                val outputFiles: ConfigurableFileCollection = project.files()

                init {
                    enabled.set(false)
                }

                @Input fun resolveEnabled(): Boolean {
                    return enabled.get()
                }

                override fun setEnabled(enabled: Boolean) {
                    this.enabled.set(enabled)
                }

                @OutputFiles fun getOutputFiles(): FileCollection {
                    return outputFiles
                }

                fun setOutputFiles(outputFiles: FileCollection) {
                    this.outputFiles.setFrom(outputFiles)
                }

                @TaskAction fun resolveValue() {
                    if(resolveEnabled()) {
                        for (outputFile in getOutputFiles()) {
                           outputFile.writeText("$OUTPUT_FILE_CONTENT")
                       }
                    }
               }
            } 
        """
        buildFile << """
            val myTask = task<MyTask>("myTask")
        """

        when:
        succeeds('myTask')

        then:
        projectUnderTest.assertDefaultOutputFileDoesNotExist()

        when:
        buildFile << """
            myTask.setEnabled(true)
            myTask.setOutputFiles(project.files("${normaliseFileSeparators(projectUnderTest.customOutputFile.canonicalPath)}"))
        """
        succeeds('myTask')

        then:
        projectUnderTest.assertDefaultOutputFileDoesNotExist()
        projectUnderTest.assertCustomOutputFileContent()
    }
}
