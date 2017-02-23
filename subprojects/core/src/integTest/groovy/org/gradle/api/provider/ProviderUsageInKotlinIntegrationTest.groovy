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

import static org.gradle.util.TestPrecondition.FIX_TO_WORK_ON_JAVA9
import static org.gradle.util.TestPrecondition.NOT_JDK_IBM
import static org.gradle.util.TextUtil.normaliseFileSeparators

@Ignore("Fails on CI for unknown reason")
@Requires([FIX_TO_WORK_ON_JAVA9, NOT_JDK_IBM])
class ProviderUsageInKotlinIntegrationTest extends KotlinScriptIntegrationTest {

    private static final String OUTPUT_FILE_CONTENT = 'Hello World!'
    File defaultOutputFile
    File customOutputFile

    def setup() {
        defaultOutputFile = file('build/output.txt')
        customOutputFile = file('build/custom.txt')
    }

    def "can create and use provider in Kotlin-based build script"() {
        given:
        withKotlinBuildSrc()
        file("buildSrc/src/main/kotlin/MyTask.kt") << """
            import org.gradle.api.DefaultTask
            import org.gradle.api.file.FileCollection
            import org.gradle.api.provider.Provider
            import org.gradle.api.tasks.TaskAction
            import org.gradle.api.tasks.Input
            import org.gradle.api.tasks.OutputFiles

            open class MyTask : DefaultTask() {
                var enabled: Provider<Boolean> = project.provider(false)
                var outputFiles: Provider<FileCollection> = project.defaultProvider(FileCollection::class.java)

                @Input fun resolveEnabled(): Boolean {
                    return enabled.get()
                }

                @OutputFiles fun getOutputFiles(): FileCollection {
                    return outputFiles.get()
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
        !defaultOutputFile.exists()

        when:
        buildFile << """
            myTask.enabled = project.provider(true)
            myTask.outputFiles = project.provider(project.files("${normaliseFileSeparators(customOutputFile.canonicalPath)}"))
        """
        succeeds('myTask')

        then:
        !defaultOutputFile.exists()
        customOutputFile.isFile()
        customOutputFile.text == OUTPUT_FILE_CONTENT
    }
}
