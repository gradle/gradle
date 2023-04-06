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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.integtests.fixtures.KotlinDslTestUtil
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.versions.KotlinGradlePluginVersions
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Issue

import static org.junit.Assume.assumeFalse

@LeaksFileHandles
class NestedInputKotlinImplementationTrackingIntegrationTest extends AbstractIntegrationSpec implements DirectoryBuildCacheFixture {

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

    @Issue("https://github.com/gradle/gradle/issues/11703")
    def "nested bean from closure can be used with the build cache"() {
        def project1 = file("project1").createDir()
        def project2 = file("project2").createDir()
        [project1, project2].each { projectDir ->
            def buildFile = projectDir.file("build.gradle.kts")
            setupTaskWithNestedAction('(File) -> Unit', '', projectDir)
            buildFile << """
                apply(plugin = "base")

                tasks.create<TaskWithNestedAction>("myTask") {
                    outputs.cacheIf { true }
                    action = { it.writeText("hello") }
                }
                """
            buildFile.makeOlder()
            projectDir.file("settings.gradle") << localCacheConfiguration()
        }

        when:
        executer.inDirectory(project1)
        withBuildCache().run 'myTask'

        then:
        executedAndNotSkipped(':myTask')
        project1.file('build/tmp/myTask/output.txt').text == "hello"

        when:
        executer.inDirectory(project2)
        withBuildCache().run 'myTask'

        then:
        skipped(':myTask')
        project2.file('build/tmp/myTask/output.txt').text == "hello"
    }

    def "task action defined in latest Kotlin can be tracked when using language version #kotlinVersion"() {
        assumeFalse(GradleContextualExecuter.embedded)
        file("buildSrc/build.gradle.kts") << """
            plugins {
                kotlin("jvm") version("${new KotlinGradlePluginVersions().latestStableOrRC}")
                `java-gradle-plugin`
            }

            import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
            import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

            repositories {
                mavenCentral()
            }

            gradlePlugin {
                plugins {
                    create("myPlugin") {
                        id = "my-plugin"
                        implementationClass = "MyPlugin"
                    }
                }
            }

            tasks.withType<KotlinCompile>().configureEach {
                compilerOptions {
                    apiVersion.set(KotlinVersion.fromVersion("${kotlinVersion}"))
                    languageVersion.set(KotlinVersion.fromVersion("${kotlinVersion}"))
                }
            }
        """
        file("buildSrc/src/main/kotlin/MyPlugin.kt") << """
            import org.gradle.api.Action
            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class MyPlugin : Plugin<Project> {
                override fun apply(target: Project) {
                    target.tasks.register("myTask") { task ->
                        task.outputs.file("build/output.txt")
                        task.doLast(Action { println("Hello") })
                    }
                }
            }
        """

        buildFile << """
            plugins {
                `my-plugin`
            }
        """

        when:
        if (kotlinVersion == "1.4") {
            executer.expectDeprecationWarning("w: Language version 1.4 is deprecated and its support will be removed in a future version of Kotlin")
        }
        run "myTask"

        then:
        executedAndNotSkipped(":myTask")

        where:
        kotlinVersion << KotlinGradlePluginVersions.LANGUAGE_VERSIONS
    }

    private void setupTaskWithNestedAction(String actionType, String actionInvocation, TestFile projectDir = temporaryFolder.testDirectory) {
        projectDir.with {
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
}
