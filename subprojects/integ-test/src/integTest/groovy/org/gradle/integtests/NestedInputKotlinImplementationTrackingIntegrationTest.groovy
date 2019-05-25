/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractPluginIntegrationTest
import org.gradle.integtests.fixtures.KotlinDslTestUtil
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

@Requires([TestPrecondition.KOTLIN_SCRIPT])
@LeaksFileHandles
class NestedInputKotlinImplementationTrackingIntegrationTest extends AbstractPluginIntegrationTest {

    @Override
    protected String getDefaultBuildFileName() {
        return 'build.gradle.kts'
    }

    def "implementations in nested Action property in Kotlin build script is tracked"() {
        setupTaskWithNestedAction('org.gradle.api.Action<File>', '.execute')
        buildFile << """
            tasks.create<TaskWithNestedAction>("myTask") {
                action = Action { writeText("original") }
            }
        """

        buildFile.makeOlder()

        when:
        run 'myTask'
        then:
        executedAndNotSkipped(':myTask')

        when:
        run 'myTask'
        then:
        skipped(':myTask')

        when:
        buildFile.text = """
            tasks.create<TaskWithNestedAction>("myTask") {
                action = Action { writeText("changed") }
            }                      
        """
        run 'myTask', '--info'
        then:
        executedAndNotSkipped(':myTask')
        file('build/tmp/myTask/output.txt').text == "changed"
        output.contains "Implementation of input property 'action' has changed for task ':myTask'"
    }

    def "implementations in nested lambda property in Kotlin build script is tracked"() {
        setupTaskWithNestedAction('(File) -> Unit', '')
        buildFile << """
            tasks.create<TaskWithNestedAction>("myTask") {
                action = { it.writeText("original") }
            }
        """

        buildFile.makeOlder()

        when:
        run 'myTask'
        then:
        executedAndNotSkipped(':myTask')

        when:
        run 'myTask'
        then:
        skipped(':myTask')

        when:
        buildFile.text = """
            tasks.create<TaskWithNestedAction>("myTask") {
                action = { it.writeText("changed") }
            }
        """
        run 'myTask', '--info'
        then:
        executedAndNotSkipped(':myTask')
        file('build/tmp/myTask/output.txt').text == "changed"
        output.contains "Implementation of input property 'action' has changed for task ':myTask'"
    }

    private void setupTaskWithNestedAction(String actionType, String actionInvocation) {
        file('buildSrc/settings.gradle.kts') << ""
        file('buildSrc/build.gradle.kts') << KotlinDslTestUtil.kotlinDslBuildSrcScript
        file("buildSrc/src/main/kotlin/TaskWithNestedAction.kt") << """
            import org.gradle.api.DefaultTask
            import org.gradle.api.tasks.Nested
            import org.gradle.api.tasks.OutputFile
            import org.gradle.api.tasks.TaskAction
            import java.io.File
            
            open class TaskWithNestedAction : DefaultTask() {
                @get: Nested
                lateinit var action: ${actionType}
            
                @get: OutputFile
                var outputFile: File = File(temporaryDir, "output.txt")
            
                @TaskAction
                fun generate() {
                    action${actionInvocation}(outputFile)
                }
            }
        """
    }
}
